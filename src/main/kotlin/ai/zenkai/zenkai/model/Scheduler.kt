package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.services.events.EventService
import ai.zenkai.zenkai.services.tasks.TaskService
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

class Scheduler(taskService: TaskService, private val eventService: EventService, language: String) {

    private val AUTO_SCHEDULED_ID = i18n[S.AUTO_SCHEDULED_ID, language]

    private val todoTasks = taskService.getTodoTasks().filter { !it.duration.isZero }

    private lateinit var remainingTasks: LinkedList<Task>

    lateinit var tasks: List<Task>
        private set

    fun schedule(start: LocalDateTime, end: LocalDateTime, timezone: ZoneId): Pair<List<Event>, List<Event>> {
        val events = eventService.getEvents(start, end)
        val alreadyScheduledEventsInRange = events.filter { AUTO_SCHEDULED_ID in it.description }.map { it.id!! }.toHashSet()
        val alreadyScheduledEventsOutOfRange = eventService.findEvents(AUTO_SCHEDULED_ID).filter { it.id !in alreadyScheduledEventsInRange }.map { it.title }.toHashSet()
        val externalEvents = events.filter { it.id !in alreadyScheduledEventsInRange }
        val scheduledTasks = mutableListOf<Event>()

        // Ignore already scheduled tasks in other days
        tasks = todoTasks.filter { !alreadyScheduledEventsOutOfRange.contains(it.title) }

        logger.info("Tasks to schedule: $tasks")

        if (tasks.isEmpty()) return scheduledTasks to events

        remainingTasks = LinkedList(tasks)

        val consumingEvents = LinkedList(externalEvents)
        var currentEvent: Event? = null
        var currentTime = start
        var currentLimit = end

        fun Task.toEvent(start: LocalDateTime, end: LocalDateTime): Event {
            return Event(title, start.atZone(timezone), end.atZone(timezone), "$AUTO_SCHEDULED_ID\n$description")
        }

        fun nextLimit() {
            currentEvent = consumingEvents.pollFirst()
            currentLimit = currentEvent?.start?.toLocalDateTime() ?: end
        }

        val firstEvent = consumingEvents.pollFirst()
        if (firstEvent != null) {
            currentTime = firstEvent.end.toLocalDateTime()
            nextLimit()
        }

        logger.info("Current time: $currentTime")
        logger.info("Limit: $currentLimit")

        var remainingTime: Boolean
        do {
            val time = currentTime.until(currentLimit, ChronoUnit.MINUTES)
            logger.info("Time to fit: $time")
            val task = nextFitting(time)
            logger.info("Task: $task")
            currentTime = if (task != null) {
                val taskStart = currentTime
                val taskEnd = currentTime.plus(task.duration)
                scheduledTasks.add(task.toEvent(taskStart, taskEnd))
                taskEnd
            } else currentEvent?.end?.toLocalDateTime() ?: end
            remainingTime = currentTime < end
            if (remainingTime && task == null) {
                nextLimit()
            }
            logger.info("Current time: $currentTime")
            logger.info("Limit: $currentLimit")
        } while (remainingTime && remainingTasks.isNotEmpty())

        if (scheduledTasks.isNotEmpty()) {
            eventService.removeEvents(alreadyScheduledEventsInRange)
        }

        return eventService.createEvents(scheduledTasks) to events
    }

    private fun nextFitting(minutes: Long): Task? {
        val it = remainingTasks.iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (next.fits(minutes)) {
                it.remove()
                return next
            }
        }
        return null
    }

    private fun Task.fits(minutes: Long) = duration.toMinutes() < minutes

    companion object {
        val TASK_COMPARATOR = Task.deadlinePriorityComparator()
    }

}