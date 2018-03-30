package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.*
import ai.zenkai.zenkai.exceptions.badRequest
import ai.zenkai.zenkai.exceptions.multicatch
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.toLocale
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.model.TaskStatus.*
import ai.zenkai.zenkai.model.TokenType.TRELLO
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.DatePeriod
import ai.zenkai.zenkai.services.calendar.displayName
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.tasks.TaskService
import ai.zenkai.zenkai.services.weather.WeatherService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import me.carleslc.kotlin.extensions.html.h
import me.carleslc.kotlin.extensions.standard.isNotNull
import me.carleslc.kotlin.extensions.standard.isNull
import me.carleslc.kotlin.extensions.standard.letIf
import me.carleslc.kotlin.extensions.standard.println
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class RootController(private val clockService: ClockService,
                     private val calendarService: CalendarService,
                     private val weatherService: WeatherService,
                     private val tasksService: TaskService) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    lateinit var gson: Gson

    @GetMapping("/")
    fun root() = "Hello, I'm Zenkai!".h(1)

    @PostMapping("/")
    fun intentMapper(req: HttpServletRequest, res: HttpServletResponse) {
        try {
            Bot.handleRequest(CachedHttpServletRequest(req), res, gson, actionMap, calendarService)
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

    fun Bot.readTasks() {
        requireToken(TRELLO) { trelloToken ->
            val taskType = getString("task-type")
            logger.debug("Task Type $taskType")
            val status = if (taskType != null) {
                TaskStatus.valueOf(taskType.toString())
            } else TaskStatus.default()
            val tasks = tasksService.getTasks(trelloToken, status)
            with (tasks) {
                var initialMessageId: S? = null
                when (status) {
                    DONE -> initialMessageId = when {
                        isEmpty() -> S.EMPTY_DONE
                        size == 1 -> S.COMPLETED_FIRST_TASK
                        size < 5 -> S.COMPLETED_TASKS
                        size < 20 -> S.COMPLETED_TASKS_CONGRATULATIONS
                        else -> S.COMPLETED_TASKS_KEEP_IT_UP
                    }
                    DOING -> {
                        initialMessageId = when {
                            isEmpty() -> S.EMPTY_DOING
                            size == 1 -> S.DOING_TASK
                            else -> S.MULTITASKING
                        }
                    }
                    TODO -> {
                        initialMessageId = when {
                            isEmpty() -> S.EMPTY_TODO
                            size == 1 -> S.TODO_SINGLE
                            count { it.status == DOING } > 1 -> S.MULTITASKING
                            else -> S.TODO
                        }
                    }
                    else -> { /* SOMEDAY, fallback to default answer */ }
                }
                if (initialMessageId != null) {
                    val initialMessage = get(initialMessageId).replace("\$size", size.toString())
                    addMessage(initialMessage)
                    if (isNotEmpty()) {
                        addMessage(get(if (size == 1) S.YOUR_TASK else S.YOUR_TASKS))
                        forEach(::addTask)
                    }
                    send()
                }
            }
        }
    }

    val actionMap: Map<String, Handler> = mapOf(
            "calculator.sum" to { b -> b.sum() },
            "calculator.substraction" to { b -> b.substract() },
            "calculator.multiplication" to { b -> b.multiply() },
            "calculator.division" to { b -> b.divide() },
            "weather" to { b -> b.weather() },
            "time.get" to { b -> b.clock() },
            "date.get" to { b -> b.calendar() },
            "date.period" to { b -> b.calendarPeriod() },
            "tasks.read" to { b -> b.readTasks() }
    )

}