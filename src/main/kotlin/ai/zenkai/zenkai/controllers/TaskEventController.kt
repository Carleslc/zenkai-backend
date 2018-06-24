package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.*
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.shiftToday
import org.springframework.stereotype.Controller
import java.time.LocalTime
import java.time.ZonedDateTime

@Controller
class TaskEventController(private val calendarService: CalendarService, private val eventController: EventController) : ActionController {

    override val actionMap: Map<String, Handler> = mapOf(
            "tasks.delete" to { b -> b.deleteTask() },
            "events.delete" to { b -> b.deleteEvent() },
            "tasks.add.rollback" to { b -> b.rollbackTaskAdd() },
            "events.add.rollback" to { b -> b.rollbackEventAdd() },
            "tasks.schedule" to { b -> b.schedule() },
            "tasks.schedule.clear" to { b -> b.clearSchedule() }
    )

    fun tryDeleteTaskOr(bot: Bot, notFoundBlock: Bot.() -> Unit = {}) = with(bot) {
        withTasks {
            val title = getString("title")
            if (title != null) {
                val tasks = getAllTasks(comparator = compareBy<Task> { it.title.length })
                val task = tasks.find { it.hasSimilarTitle(title, locale) }
                if (task != null) {
                    archiveTask(task)
                    addMessage(S.TASK_DELETED)
                    addMessage(S.YOUR_TASK)
                    addTask(task)
                } else {
                    notFoundBlock()
                }
            }
        }
    }

    fun Bot.rollbackTaskAdd() {
        val moved = getString("moved-from")
        val title = getString("title")
        if (moved != null && title != null) {
            withTasks {
                val status = TaskStatus.valueOf(moved)
                val tasks = getAllTasks(null)
                val task = tasks.find { it.hasSimilarTitle(title, locale) }
                if (task != null) {
                    val messageId = if (task.status == status) {
                        archiveTask(task)
                        setArgument("moved-from", null)
                        S.TASK_DELETED
                    } else {
                        moveTask(task, status)
                        setArgument("moved-from", task.status.toString())
                        S.MOVED_TASK
                    }
                    addMessage(get(messageId).replace("\$type", status.getListName(language).capitalize()))
                    addMessage(S.YOUR_TASK)
                    addTask(task)
                }
            }
        } else tryDeleteTaskOr(this)
    }

    fun Bot.deleteTask() = tryDeleteTaskOr(this) {
        tryDeleteEventOr(this) {
            addMessage(S.TASK_NOT_FOUND)
        }
    }

    fun Bot.rollbackEventAdd() = tryDeleteEventOr(this)

    fun tryDeleteEventOr(bot: Bot, notFoundBlock: Bot.() -> Unit = {}) = with(bot) {
        withEvents {
            val title = getString("title")
            if (title != null) {
                val event = findEvent(title.capitalize())
                if (event != null) {
                    removeEvent(event)
                    addMessage(S.EVENT_DELETED)
                    addMessage(S.YOUR_EVENT)
                    addEvent(event)
                } else {
                    notFoundBlock()
                }
            }
        }
    }

    fun Bot.deleteEvent() = tryDeleteEventOr(this) {
        tryDeleteTaskOr(this) {
            addMessage(S.EVENT_NOT_FOUND)
        }
    }

    fun Bot.schedule() = withTasksEvents { taskService, eventService ->
        val date = getDate("date")!!
        val now = ZonedDateTime.now(timezone)

        if (date.isBefore(now.toLocalDate())) {
            addMessage(S.PAST_SCHEDULE_DATE)
            return@withTasksEvents
        }

        val period = getTimePeriod("start", "end", "time-period",false)
        val startTime = period?.start ?: LocalTime.of(8, 0)
        val endTime = period?.end ?: LocalTime.of(21, 0)

        val start = date.atTime(startTime).shiftToday(now).toLocalDateTime()
        val end = date.atTime(endTime).shiftToday(now).toLocalDateTime()

        logger.info("Scheduling tasks from $start to $end")

        val scheduler = Scheduler(taskService, eventService, language)
        val (scheduledEvents, dateEvents) = scheduler.schedule(start, end, timezone)

        val events = mutableListOf<Event>().also {
            it.addAll(scheduledEvents)
            it.addAll(dateEvents)
        }.sortedBy { it.start }

        val messageId = when {
            scheduler.tasks.isEmpty() -> S.NO_TASKS_SCHEDULE
            scheduledEvents.isEmpty() -> S.NO_SCHEDULED
            scheduledEvents.size == 1 -> S.SCHEDULED_SINGLE
            else -> S.SCHEDULED
        }

        fun deadlineMissed(task: Task): Boolean {
            return task.deadline?.isBefore(end) == true && scheduledEvents.all { it.title != task.title || task.deadline.isAfter(it.end.toLocalDateTime()) == true }
        }

        val missedTasks = scheduler.tasks.filter(::deadlineMissed)
        if (missedTasks.isNotEmpty()) {
            addMessage(S.DEADLINE_MISSED_WARNING)
            missedTasks.forEach { addTask(it) }
        }
        addMessage(get(messageId).replace("\$size", scheduledEvents.size.toString()))
        if (scheduledEvents.isNotEmpty()) {
            addMessage(get(eventController.getEventsDateMessageId(events)).replace("\$size", events.size.toString()).replace("\$date", calendarService.prettyDate(date, language)))
            events.forEach { addEvent(it) }
        }
    }

    fun Bot.clearSchedule() = withEvents {
        removeEvents(get(S.AUTO_SCHEDULED_ID))
        addMessage(S.REMOVED_SCHEDULING)
    }

}