package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.CachedHttpServletRequest
import ai.zenkai.zenkai.exceptions.badRequest
import ai.zenkai.zenkai.exceptions.multicatch
import ai.zenkai.zenkai.fixInt
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.isSpanish
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.model.TaskStatus.*
import ai.zenkai.zenkai.model.TokenType.TRELLO
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.tasks.TaskService
import ai.zenkai.zenkai.services.weather.WeatherService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import me.carleslc.kotlin.extensions.html.h
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId
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
        tell("The sum of ${fixInt(a)} and ${fixInt(b)} is ${fixInt(result)}")
    }

    fun Bot.substract() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a - b
        tell("The difference between ${fixInt(a)} and ${fixInt(b)} is ${fixInt(result)}")
    }

    fun Bot.multiply() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a * b
        tell("${fixInt(a)} times ${fixInt(b)} is ${fixInt(result)}")
    }

    fun Bot.divide() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        if (b == 0.toDouble()) {
            tell("Cannot divide by 0!")
        } else {
            val result = a / b
            tell("${fixInt(a)} divided by ${fixInt(b)} is ${fixInt(result)}")
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

    fun Bot.calendar() {
        var original = getString("date-original")
        var date = getDate("date")

        if (language.isSpanish() && "semana pasada" in query) { // fix Dialogflow misunderstanding
            date = date.minusWeeks(1)
        }

        val be = isOrWas(date)

        var raw = ""
        val format = if (original == null || original.isBlank()) {
            original = get(S.TODAY)
            calendarService.prettyDate(date, language)
        } else if (original.contains("[0-9]".toRegex())) {
            raw = " (${calendarService.formatDate(date, language)})"
            calendarService.getDayOfWeek(date, language)
        } else {
            calendarService.getDayOfMonth(date, language)
        }

        val speech = "${original.capitalize()} $be $format"
        val display = "$speech$raw"
        tell(speech, display)
    }

    fun Bot.readTasks() {
        requireToken(TRELLO) { trelloToken ->
            val taskType = getString("task-type")
            logger.info("Task Type $taskType")
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
                        forEach { addMessage(it.getSpeech(language, timezone), it.getDisplayText(language, timezone)) }
                    }
                    send()
                }
            }
        }
    }

    fun Task.getDisplayText(language: String, zoneId: ZoneId) = buildString {
        appendln(title)
        if (deadline != null) {
            append(i18n[S.DEADLINE, language]).append(' ').appendln(calendarService.prettyApproxDateTime(deadline, zoneId, language))
        }
        appendln(description)
        if (tags.isNotEmpty()) {
            appendln(tags.joinToString(prefix = "Tags: "))
        }
    }

    fun Task.getSpeech(language: String, zoneId: ZoneId) = buildString {
        append(title)
        if (deadline != null) {
            append(' ').append(i18n[S.DEADLINE_SPEECH, language]).append(' ').append(calendarService.prettyApprox(deadline, zoneId, language))
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
            "tasks.read" to { b -> b.readTasks() }
    )

}