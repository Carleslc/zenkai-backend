package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.LazyLogger
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Event
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.model.TASK_READ_CONTEXT
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.shiftTime
import ai.zenkai.zenkai.services.calendar.shiftTimeBack
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import me.carleslc.kotlin.extensions.standard.isNotNull
import org.slf4j.Logger
import org.springframework.stereotype.Controller
import java.time.ZonedDateTime

@Controller
class EventController(val calendarService: CalendarService) : ActionController {

    val logger: Logger by LazyLogger()

    override val actionMap: Map<String, Handler> = mapOf(
            "events.read" to { b -> b.readEvents() },
            "events.add" to { b -> b.addEvent() },
            "events.add.now" to { b -> b.addEventNow() },
            "events.add.period" to { b -> b.addPeriodEvent() },
            "events.add.quick" to { b -> b.addQuickEvent() }
    )

    fun getEventsDateMessageId(events: List<Event>): S {
        return when {
            events.isEmpty() -> S.NO_EVENTS_DATE
            events.size == 1 -> S.SINGLE_EVENT_DATE
            else -> S.YOUR_EVENTS_DATE
        }
    }

    fun Bot.readEvents() = withEvents {
        val date = getDate("date", context = TASK_READ_CONTEXT)
        val events = if (date != null) {
            getEvents(date)
        } else {
            getFollowingEvents(3, maxDate = ZonedDateTime.now(timezone).plusMonths(1).toLocalDateTime())
        }
        val messageId = if (date.isNotNull()) {
            getEventsDateMessageId(events)
        } else when {
            events.isEmpty() -> S.NO_EVENTS
            events.size == 1 -> S.SINGLE_EVENT
            else -> S.YOUR_EVENTS
        }
        var message = get(messageId).replace("\$size", events.size.toString())
        if (date != null) {
            message = message.replace("\$date", calendarService.prettyDate(date, language))
        }
        addMessage(message)
        events.forEach { addEvent(it) }
    }

    private fun Bot.putEvent(from: ZonedDateTime?, to: ZonedDateTime?,
                             endTimeSpecified: Boolean,
                             startDateOriginal: String? = getString("start-date-original"),
                             endDateOriginal: String? = getString("end-date-original"),
                             startTimeOriginal: String? = getString("start-time-original"),
                             endTimeOriginal: String? = getString("end-time-original")) = withEvents {
        var start = from
        var end = to
        val now = ZonedDateTime.now(timezone)!!
        logger.info("Now: $now")
        var title = getString("event-title")
        if (title != null && get(S.SCHEDULE) in title.toLowerCase() && getDate("date") != null) { // not an event but schedule action
            SchedulerController.schedule(this@putEvent, this@EventController)
        }
        val location = getString("location")
        logger.info("Title: $title")
        logger.info("Start: $start (Original $startDateOriginal / $startTimeOriginal)")
        logger.info("End:   $end (Original $endDateOriginal / $endTimeOriginal)")
        if (title != null && start != null && end != null && !isCancelled(query)) {
            title = title.capitalize()
            start = calendarService.implicitDateTime(now, start, startDateOriginal, startTimeOriginal, language)
            end = calendarService.implicitDateTime(now, end, endDateOriginal ?: startDateOriginal, endTimeOriginal, language)
            if (start < now.minusMinutes(1)) {
                logger.info("Start < now")
                if (start.toLocalDate() < now.toLocalDate()) {
                    logger.info("Start Date < Today")
                    start = now.toLocalDate().atTime(start.toLocalTime()).atZone(timezone)!!
                }
                start = start.shiftTime(now)
            }
            if (end.toLocalDate() < start.toLocalDate()) {
                logger.info("End Date < Start Date")
                end = start.toLocalDate().atTime(end.toLocalTime()).atZone(timezone)!!
            }
            if (!endTimeSpecified && start.toLocalDate() == end.toLocalDate()) {
                logger.info("end-time not specified -> end = start + 1h")
                end = end.toLocalDate().atTime(start.toLocalTime()).plusHours(1).atZone(timezone)!!
            }
            if (end <= start) {
                logger.info("End Time <= Start Time")
                val endMorning = calendarService.isMorningNullable(endTimeOriginal, language)
                if (endMorning || calendarService.isMorningNullable(startTimeOriginal, language)) {
                    start = start.shiftTimeBack(end).shiftTime(now)
                }
                logger.info("End $end shift Start $start, morning $endMorning")
                end = end.shiftTime(start, endMorning)
            }
            logger.info("Start Finish: $start")
            logger.info("End Finish:   $end")
            val overlapping = getEvents(start.toLocalDateTime(), end.toLocalDateTime())
            if (overlapping.isEmpty()) {
                addMessage(S.ADDED_EVENT)
            } else {
                addMessage(get(S.OVERLAPPING_EVENTS).replace("\$title", title))
                overlapping.forEach { addEvent(it) }
            }
            val event = createEvent(Event(title, start, end, "", location))
            addMessage(S.YOUR_EVENT)
            addEvent(event)
        }
    }

    fun Bot.addEvent() {
        val start = getDateTime("start-date", "start-time")
        val end = getDateTime("end-date", "end-time", implicit = false, defaultDate = start?.toLocalDate())
        putEvent(start, end, getTime("end-time") != null)
    }

    fun Bot.addEventNow() {
        val start = getDateTime("start-date", "start-time",
                defaultDate = calendarService.today(timezone),
                defaultTime = calendarService.clockService.now(timezone))
        val end = getDateTime("end-date", "end-time", implicit = false, defaultDate = start?.toLocalDate())
        putEvent(start, end, getTime("end-time") != null)
    }

    fun Bot.addPeriodEvent() {
        val datePeriod = getDatePeriod("date-period")
        val datePeriodOriginal = getString("date-period-original")
        putEvent(datePeriod?.start?.atStartOfDay(timezone), datePeriod?.end?.atStartOfDay(timezone),
                true, datePeriodOriginal, datePeriodOriginal)
    }

    fun Bot.addQuickEvent() = withEvents {
        try {
            val event = createEvent(query)
            addMessages(S.ADDED_EVENT, S.YOUR_EVENT)
            addEvent(event)
        } catch (e: GoogleJsonResponseException) {
            addMessage(S.CANNOT_ADD_EVENT)
        }
    }

}