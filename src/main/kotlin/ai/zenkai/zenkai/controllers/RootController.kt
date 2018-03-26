package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.S
import ai.zenkai.zenkai.exceptions.*
import ai.zenkai.zenkai.i18n
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.model.TaskStatus.*
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.tasks.TaskService
import ai.zenkai.zenkai.services.weather.WeatherService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.tmsdurham.actions.DialogflowApp
import com.tmsdurham.actions.RichResponse
import com.tmsdurham.dialogflow.Data
import com.tmsdurham.dialogflow.DialogflowResponse
import main.java.com.tmsdurham.dialogflow.sample.DialogflowAction
import me.carleslc.kotlin.extensions.html.h
import me.carleslc.kotlin.extensions.number.round
import me.carleslc.kotlin.extensions.standard.isNull
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
                     private var calendarService: CalendarService,
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
            DialogflowAction(req, res, gson).handleRequest(actionMap)
        } catch (e: Exception) {
            e.multicatch(InvalidArgumentException::class,
                    MissingRequiredArgumentException::class,
                    InvalidTokenException::class,
                    IllegalStateException::class,
                    JsonSyntaxException::class) {
                logger.warn("${e.message}, for request ${req.body}")
                badRequest(e, gson, res)
            }
        }
    }

    fun fixInt(n: Double): String = if (n.toInt().toDouble() == n) n.toInt().toString() else n.round(5)

    fun sum(action: DialogflowApp) {
        val a = action.getParameter("number1").toDouble()
        val b = action.getParameter("number2").toDouble()
        val result = a + b
        action.tell("The sum of ${fixInt(a)} and ${fixInt(b)} is ${fixInt(result)}")
    }

    fun substract(action: DialogflowApp) {
        val a = action.getParameter("number1").toDouble()
        val b = action.getParameter("number2").toDouble()
        val result = a - b
        action.tell("The difference between ${fixInt(a)} and ${fixInt(b)} is ${fixInt(result)}")
    }

    fun multiply(action: DialogflowApp) {
        val a = action.getParameter("number1").toDouble()
        val b = action.getParameter("number2").toDouble()
        val result = a * b
        action.tell("${fixInt(a)} times ${fixInt(b)} is ${fixInt(result)}")
    }

    fun divide(action: DialogflowApp) {
        val a = action.getParameter("number1").toDouble()
        val b = action.getParameter("number2").toDouble()
        if (b == 0.toDouble()) {
            action.tell("Cannot divide by 0!")
        } else {
            val result = a / b
            action.tell("${fixInt(a)} divided by ${fixInt(b)} is ${fixInt(result)}")
        }
    }

    fun weather(action: DialogflowApp) = verify(action) {
        val location = action.getParameter("city")
        action.fill(weatherService.getWeather(location, language), get(S.CITY_NOT_FOUND)) {
            get(S.WEATHER)
                    .replace("\$city", city)
                    .replace("\$temperature", temperature.toString())
                    .replace("\$description", description)
        }
    }

    fun clock(action: DialogflowApp) = verify(action) {
        val time = clockService.getCurrentTime(timezone).time
        val speech = "${get(S.CURRENT_TIME)} $time".trim()
        action.tell(speech)
    }

    fun readTasks(action: DialogflowApp) = verify(action) {
        if (trelloToken == null) throw InvalidTokenException()
        val readTasksContext = "tasksread-followup"
        val taskType = action.getContextArgument(readTasksContext, "task-type")?.value
        val status = if (taskType != null) {
            logger.debug("TaskStatus: $taskType")
            TaskStatus.valueOf(taskType.toString())
        } else TaskStatus.default()
        val tasks = tasksService.getTasks(trelloToken, status)
        with (tasks) {
            val response = RichResponse()
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
                response.addSimpleResponse(initialMessage, initialMessage)
                if (isNotEmpty()) {
                    val taskHeader = get(if (size == 1) S.YOUR_TASK else S.YOUR_TASKS)
                    response.addSimpleResponse(taskHeader, taskHeader)
                    forEach { response.addSimpleResponse(it.getSpeech(language, timezone), it.getDisplayText(language, timezone)) }
                }
                action.data {
                    //  TODO custom data
                    put("zenkai", listOf("Example data 1", "And 2"))
                }
                //action.tell("Hello")
                action.tell(response)
            }
        }
    }

    fun DialogflowApp.getParameter(vararg keys: String): String {
        val param = getArgument(keys[0])
        return if (keys.size > 1) (param as Map<*,*>)[keys[1]].toString() else param.toString()
    }

    fun <T> DialogflowApp.fill(obj: T?, default: String, fill: T.() -> String) {
        tell(if (obj.isNull()) default else fill(obj!!))
    }

    fun Task.getDisplayText(language: String, zoneId: ZoneId) = buildString {
        appendln(title)
        if (deadline != null) {
            append(i18n[S.DEADLINE, language]).append(' ').appendln(calendarService.pretty(deadline, zoneId, language))
        }
        appendln(description)
        if (tags.isNotEmpty()) {
            appendln(tags.joinToString(prefix = "Tags: "))
        }
    }

    fun Task.getSpeech(language: String, zoneId: ZoneId) = buildString {
        append(title)
        if (deadline != null) {
            append(' ').append(i18n[S.DEADLINE_SPEECH, language]).append(' ').append(calendarService.prettyDuration(deadline, zoneId))
        }
    }

    @Throws(InvalidArgumentException::class, MissingRequiredArgumentException::class)
    fun verify(action: DialogflowApp, success: RequestParameters.() -> Unit) {
        success(RequestParameters.from(action))
        // TODO: Add custom error message with action.data
    }

    fun DialogflowApp.getRequest() = gson.toJson(request.body)

    val HttpServletRequest.body get() = inputStream.reader().readText()

    val actionMap = mapOf(
            "calculator.sum" to ::sum,
            "calculator.substraction" to ::substract,
            "calculator.multiplication" to ::multiply,
            "calculator.division" to ::divide,
            "weather" to ::weather,
            "time.get" to ::clock,
            "tasks.read" to ::readTasks
    )

}