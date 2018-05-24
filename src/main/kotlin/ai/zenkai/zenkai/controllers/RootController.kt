package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.CachedHttpServletRequest
import ai.zenkai.zenkai.clean
import ai.zenkai.zenkai.exceptions.badRequest
import ai.zenkai.zenkai.exceptions.multicatch
import ai.zenkai.zenkai.fixInt
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.model.TaskStatus.*
import ai.zenkai.zenkai.model.TokenType.TRELLO
import ai.zenkai.zenkai.replace
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.DatePeriod
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.weather.WeatherService
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
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class RootController(private val clockService: ClockService,
                     private val calendarService: CalendarService,
                     private val weatherService: WeatherService) {

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

    fun Bot.sum() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a + b

        tell(get(S.SUM).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.substract() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a - b
        tell(get(S.SUBSTRACT).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.multiply() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a * b
        tell(get(S.MULTIPLY).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.divide() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        if (b == 0.toDouble()) {
            tell(S.DIVIDE_ZERO)
        } else {
            val result = a / b
            tell(get(S.DIVIDE).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
        }
    }

    fun Bot.weather() {
        val location = getString("city")
        fill(weatherService.getWeather(location!!, language), get(S.CITY_NOT_FOUND)) {
            get(S.WEATHER)
                    .replace("\$city", city)
                    .replace("\$temperature", temperature.toString())
                    .replace("\$description", description)
        }
    }

    fun Bot.greetings() {
        addMessage(S.GREETINGS)
        if (!containsToken(TRELLO)) {
            needsLogin(S.GREETINGS_LOGIN, TRELLO)
        } else {
            tell(S.GREETINGS_LOGGED)
        }
    }

    fun Bot.clock() {
        val time = clockService.now(timezone)
        val prefix = get(if (time.hour == 1 || time.hour == 13) S.CURRENT_TIME_SINGLE else S.CURRENT_TIME)
        val formattedTime = clockService.format(time, language)
        val speech = "${prefix.capitalize()} $formattedTime".trim()
        tell(speech, formattedTime)
    }

    fun Bot.calendarPeriod() {
        val today = calendarService.today(timezone)
        val dayOfWeek = getString("day-of-week")?.let { DayOfWeek.valueOf(it) }
        val period = getDate("period")?.let { DatePeriod(it, calendarService) }
        val original = getString("period-original")?.toLowerCase(locale).orEmpty()

        val date = calendarService.inPeriod(dayOfWeek ?: today.dayOfWeek,
                period ?: DatePeriod.default(calendarService), today)

        val format = if (dayOfWeek.isNull()) {
            calendarService.prettyDate(date, language)
        } else {
            calendarService.getDayOfMonth(date, language)
        }

        val originalString = original.clean().capitalize()
        var be = isOrWas(date)
        if (originalString.isBlank()) be = be.capitalize()
        tell("$originalString $be $format".trim())
    }

    fun Bot.calendar() {
        val today = calendarService.today(timezone)
        var date = getDate("date") ?: today
        val original = getString("date-original")?.toLowerCase(locale) ?: get(S.TODAY)
        val dateAsk = getString("date-ask")?.clean()?.toLowerCase(locale)
        val isAsking = dateAsk.isNotNullOrBlank()

        fun processAskingOr(notAsking: () -> String): String {
            return if (isAsking) {
                if (calendarService.isDayOfWeek(dateAsk!!, language)) {
                    logger.info("Asking Day of Week")
                    calendarService.getDayOfWeek(date, language)
                } else {
                    logger.info("Asking Day of Month")
                    calendarService.getDayOfMonth(date, language)
                }
            } else notAsking()
        }

        val queryDayOfWeek = calendarService.isDayOfWeek(original, language)

        if (calendarService.isPast(query, language) && date >= today) {
            date = if (queryDayOfWeek) date.minusWeeks(1) else date.minusYears(1)
        }

        val format = when {
            queryDayOfWeek -> {
                logger.info("Query Day of Week")
                processAskingOr { calendarService.getDayOfMonth(date, language) }
            }
            calendarService.isDayOfMonth(original, language) -> {
                logger.info("Query Day of Month")
                processAskingOr { calendarService.getDayOfWeek(date, language) }
            }
            else -> processAskingOr { calendarService.prettyDate(date, language) }
        }

        tell("${original.clean().capitalize()} ${isOrWas(date)} $format")
    }

    fun Bot.readTasks() = withTrello {
        val taskType = getString("task-type")
        logger.info("Task Type $taskType")
        val status = TaskStatus.parse(taskType)
        with (getDefaultBoard().getReadableTasks(status)) {
            var statusMessageId: S? = null
            when (status) {
                DONE -> statusMessageId = when {
                    isEmpty() -> S.EMPTY_DONE
                    size == 1 -> S.COMPLETED_FIRST_TASK
                    size < 5 -> S.COMPLETED_TASKS
                    size < 20 -> S.COMPLETED_TASKS_CONGRATULATIONS
                    else -> S.COMPLETED_TASKS_KEEP_IT_UP
                }
                DOING -> {
                    statusMessageId = when {
                        isEmpty() -> S.EMPTY_DOING
                        size == 1 -> S.DOING_TASK
                        else -> S.MULTITASKING
                    }
                }
                TODO -> {
                    statusMessageId = when {
                        isEmpty() -> S.EMPTY_TODO
                        size == 1 -> S.TODO_SINGLE
                        else -> S.TODO_FOCUS
                    }
                }
                SOMEDAY -> {
                    statusMessageId = when {
                        isEmpty() -> S.EMPTY_SOMEDAY
                        size == 1 -> S.SOMEDAY_SINGLE
                        else -> S.SOMEDAY_TASKS
                    }
                }
            }
            addMessage(get(statusMessageId).replace("\$size", size.toString()))
            if (status == TODO) {
                val doingTasks = count { it.status == DOING }
                if (doingTasks > 1) {
                    addMessage(get(S.MULTITASKING).replace("\$size", doingTasks.toString()))
                } else if (size > 10 && getDefaultBoard().getReadableTasks(SOMEDAY).isEmpty()) {
                    addMessage(S.EMPTY_SOMEDAY)
                }
            }
            if (isNotEmpty()) {
                addMessage(get(if (size == 1) S.YOUR_TASK else S.YOUR_TASKS))
                forEach(::addTask)
            }
            send()
        }
    }

    fun Bot.addTask() = withTrello {
        val title = getString("task-title")
        if (title != null) {
            val taskType = getString("task-type")
            val deadline = getDateTime("date", "time")
            val status = TaskStatus.parse(taskType)
            val description = query
            var task = Task(title.capitalize(), description, status, deadline)
            val messageId: S
            val previousTasks = getDefaultBoard().getPreviousTasks(status)
            val matchTask = previousTasks.find { it.isSimilar(task) }
            if (matchTask != null) {
                task = matchTask
                messageId = if (matchTask.status == status) {
                    S.ALREADY_ADDED
                } else {
                    getDefaultBoard().moveTask(task, status)
                    S.MOVED_TASK
                }
            } else {
                task = getDefaultBoard().addTask(task)
                messageId = S.ADDED_TASK
            }
            addMessage(get(messageId).replace("\$type", status.getListName(language)))
            addMessage(S.YOUR_TASK)
            addTask(task)
            send()
        }
    }

    fun Bot.deleteTask() = withTrello {
        val title = getString("task-title")
        if (title != null) {
            val previousTasks = getDefaultBoard().getPreviousTasks()
            val task = previousTasks.find { it.hasSimilarTitle(title) }
            if (task != null) {
                getDefaultBoard().archiveTask(task)
                addMessage(S.TASK_DELETED)
                addMessage(S.YOUR_TASK)
                addTask(task)
            } else {
                addMessage(S.TASK_NOT_FOUND)
            }
            send()
        }
    }

    fun Bot.readEvents() = withCalendar {
        val date = getDate("date")
        val events = if (date != null) {
            readEvents(date)
        } else {
            readFollowingEvents(5)
        }
        val messageId = if (date.isNotNull()) {
            when {
                events.isEmpty() -> S.NO_EVENTS_DATE
                events.size == 1 -> S.YOUR_EVENT_DATE
                else -> S.YOUR_EVENTS_DATE
            }
        } else when {
            events.isEmpty() -> S.NO_EVENTS
            events.size == 1 -> S.YOUR_EVENT
            else -> S.YOUR_EVENTS
        }
        var message = get(messageId).replace("\$size", events.size.toString())
        if (date != null) {
            message = message.replace("\$date", calendarService.prettyDate(date, language))
        }
        addMessage(message)
        events.forEach { addEvent(it) }
        send()
    }

    fun Bot.addEvent() = withCalendar {
        val title = getString("event-title")
        if (title != null) {
            addMessage("Calendar Add: $title")
            send()
        }
    }

    val actionMap: Map<String, Handler> = mapOf(
            "calculator.sum" to { b -> b.sum() },
            "calculator.substraction" to { b -> b.substract() },
            "calculator.multiplication" to { b -> b.multiply() },
            "calculator.division" to { b -> b.divide() },
            "weather" to { b -> b.weather() },
            "greetings" to { b -> b.greetings() },
            "time.get" to { b -> b.clock() },
            "date.get" to { b -> b.calendar() },
            "date.get.period" to { b -> b.calendarPeriod() },
            "tasks.read" to { b -> b.readTasks() },
            "tasks.add" to { b -> b.addTask() },
            "tasks.delete" to { b -> b.deleteTask() },
            "events.read" to { b -> b.readEvents() },
            "events.add" to { b -> b.addEvent() }
    )

}