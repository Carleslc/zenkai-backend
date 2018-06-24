package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.CachedHttpServletRequest
import ai.zenkai.zenkai.exceptions.badRequest
import ai.zenkai.zenkai.exceptions.multicatch
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.mergeMaps
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.clock.ClockService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import me.carleslc.kotlin.extensions.html.h
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class RootController(private val calendarService: CalendarService,
                     private val clockService: ClockService,
                     calculatorController: CalculatorController,
                     weatherController: WeatherController,
                     timeController: TimeController,
                     calendarController: CalendarController,
                     loginController: LoginController,
                     taskController: TaskController,
                     eventController: EventController,
                     taskEventController: TaskEventController): ActionController {

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

    fun Bot.help() {
        addMessage(textToSpeech = get(S.HELP_SPEECH), displayText = get(S.HELP))
    }

    override val actionMap: Map<String, Handler> = mergeMaps(
            actionMapper(calculatorController, weatherController, timeController, calendarController, loginController, taskController, eventController, taskEventController),
            mapOf<String, Handler>(
                    "help" to { b -> b.help() }
            )
    )

}