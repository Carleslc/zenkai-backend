package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.*
import ai.zenkai.zenkai.exceptions.badRequest
import ai.zenkai.zenkai.exceptions.multicatch
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.trimStopWordsLeft
import ai.zenkai.zenkai.model.*
import ai.zenkai.zenkai.model.TaskStatus.*
import ai.zenkai.zenkai.services.calendar.*
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.clock.isSingleHour
import ai.zenkai.zenkai.services.weather.WeatherService
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import me.carleslc.kotlin.extensions.html.h
import me.carleslc.kotlin.extensions.standard.isNotNull
import me.carleslc.kotlin.extensions.standard.isNull
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class RootController(private val calculatorController: CalculatorController,
                     private val weatherController: WeatherController,
                     private val timeController: TimeController,
                     private val calendarController: CalendarController,
                     private val loginController: LoginController,
                     private val taskController: TaskController,
                     private val eventController: EventController) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    lateinit var gson: Gson

    @GetMapping("/")
    fun root() = "Hello, I'm Zenkai!".h(1)

    @PostMapping("/")
    fun intentMapper(req: HttpServletRequest, res: HttpServletResponse) {
        try {
            Bot.handleRequest(CachedHttpServletRequest(req), res, gson, actionMap, calendarService, clockService)
        } catch (e: Exception) {
            e.multicatch(IllegalStateException::class, JsonSyntaxException::class) {
                badRequest(e, gson, res)
            }
        }
    }



    private fun Bot.tryDeleteEventOr(notFoundBlock: Bot.() -> Unit = {}) = withEvents {
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

    fun Bot.deleteEvent() = tryDeleteEventOr {
        tryDeleteTaskOr {
            addMessage(S.EVENT_NOT_FOUND)
        }
    }

    private fun getEventsDateMessageId(events: List<Event>): S {
        return when {
            events.isEmpty() -> S.NO_EVENTS_DATE
            events.size == 1 -> S.SINGLE_EVENT_DATE
            else -> S.YOUR_EVENTS_DATE
        }
    }

    fun Bot.readEvents() = withEvents {
        val date = getDate("date", context = TASK_READ_CONTEXT)
        val events = if (date != null) {
            readEvents(date)
        } else {
            readFollowingEvents(3, maxDate = ZonedDateTime.now(timezone).plusMonths(1).toLocalDateTime())
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
                defaultTime = clockService.now(timezone))
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

    fun Bot.rollbackEventAdd() = tryDeleteEventOr()



    val actionMap: Map<String, Handler> = actionMapper(calculatorController, weatherController, timeController, calendarController, loginController, taskController, eventController)
            "events.read" to { b -> b.readEvents() },
            "events.add" to { b -> b.addEvent() },
            "events.add.now" to { b -> b.addEventNow() },
            "events.add.period" to { b -> b.addPeriodEvent() },
            "events.add.quick" to { b -> b.addQuickEvent() },
            "events.add.rollback" to { b -> b.rollbackEventAdd() },
            "events.delete" to { b -> b.deleteEvent() }

}