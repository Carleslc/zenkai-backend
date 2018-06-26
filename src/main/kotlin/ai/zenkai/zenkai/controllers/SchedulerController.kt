package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.*
import ai.zenkai.zenkai.services.calendar.shiftToday
import java.time.LocalTime
import java.time.ZonedDateTime

object SchedulerController {

    fun schedule(bot: Bot, eventController: EventController) = with(bot) {
        withTasksEvents { taskService, eventService ->
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
                addMessage(get(eventController.getEventsDateMessageId(events)).replace("\$size", events.size.toString()).replace("\$date", eventController.calendarService.prettyDate(date, language)))
                events.forEach { addEvent(it) }
            }
        }
    }
}