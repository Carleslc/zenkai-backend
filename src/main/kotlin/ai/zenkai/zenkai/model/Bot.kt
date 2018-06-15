package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.controllers.auth.GoogleApiAuthorization
import ai.zenkai.zenkai.exceptions.BadRequestError
import ai.zenkai.zenkai.exceptions.BotError
import ai.zenkai.zenkai.exceptions.LoginError
import ai.zenkai.zenkai.i18n.DEFAULT_LANGUAGE
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.toLocale
import ai.zenkai.zenkai.nullIfBlank
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.DatePeriod
import ai.zenkai.zenkai.services.clock.*
import ai.zenkai.zenkai.services.events.CalendarListener
import ai.zenkai.zenkai.services.events.EventService
import ai.zenkai.zenkai.services.events.GoogleCalendarEventService
import ai.zenkai.zenkai.services.tasks.Board
import ai.zenkai.zenkai.services.tasks.TaskService
import ai.zenkai.zenkai.services.tasks.TasksListener
import ai.zenkai.zenkai.services.tasks.TrelloTaskService
import arrow.data.Try
import arrow.data.getOrElse
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tmsdurham.actions.DialogflowApp
import com.tmsdurham.actions.SimpleResponse
import main.java.com.tmsdurham.dialogflow.sample.DialogflowAction
import me.carleslc.kotlin.extensions.standard.isNull
import me.carleslc.kotlin.extensions.standard.letIf
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

typealias Handler = (Bot) -> Unit

const val USER_CONTEXT = "user-logged-in"
const val TASK_ADD_CONTEXT = "tasksadd-followup"
const val TASK_ASK_DURATION_CONTEXT = "tasksadd-duration-followup"
const val TASK_READ_CONTEXT = "tasksread-followup"

data class Bot(val language: String,
               val timezone: ZoneId,
               private val baseUrl: String,
               private val action: DialogflowApp,
               private val tokens: MutableMap<TokenType, String>,
               private val calendarService: CalendarService,
               private val clockService: ClockService,
               private val gson: Gson,
               private var error: BotError? = null,
               private var onNeedLogin: () -> Unit = {}) : TasksListener, CalendarListener {

    private val messages by lazy { mutableListOf<SimpleResponse>() }

    val query get() = action.query

    val locale by lazy { language.toLocale() }

    var lastRequiredToken: TokenType? = null

    var sent = false

    fun addMessage(textToSpeech: String? = null, displayText: String? = null): Boolean {
        if (textToSpeech.isNotNullOrBlank() || displayText.isNotNullOrBlank()) {
            messages.add(SimpleResponse(textToSpeech = textToSpeech.nullIfBlank(), displayText = displayText.nullIfBlank()))
            return true
        }
        return false
    }

    fun addSpeech(speech: String) = addMessage(textToSpeech = speech)

    fun addText(text: String) = addMessage(displayText = text)

    fun addMessage(id: S) = addMessage(get(id))

    fun addMessage(s: String) = addMessage(s, s)

    fun addMessages(vararg ids: S) {
        ids.forEach { addMessage(it) }
    }

    fun addMessages(s: String) {
        s.split('\n').forEach { addMessage(it) }
    }

    fun addMessages(id: S) = addMessages(get(id))

    fun addTask(task: Task) {
        with(task) {
            addMessage(getSpeech(language, timezone, calendarService), getDisplayText(language, timezone, calendarService))
        }
    }

    fun addEvent(event: Event) {
        with(event) {
            addMessage(event.getSpeech(language, calendarService), event.getDisplayText(language, calendarService))
        }
    }

    fun <T> fill(obj: T?, default: String, fill: T.() -> String) {
        addMessage(if (obj.isNull()) default else fill(obj!!))
    }

    fun withTasks(block: TaskService.() -> Unit) = requireToken(TokenType.TRELLO) {
        block(TrelloTaskService(it, this, timezone, clockService))
    }

    private fun needsLoginCalendar(auth: GoogleApiAuthorization) {
        needsLogin(S.LOGIN_CALENDAR, auth.getSimpleAuthorizationUrl(gson, language, timezone))
        auth.clear()
    }

    private fun withCalendarAuth(block: GoogleApiAuthorization.() -> Unit) = requireToken(TokenType.TRELLO) {
        val auth = GoogleApiAuthorization(it)
        if (auth.hasValidCredentials()) {
            block(auth)
        } else {
            needsLoginCalendar(auth)
        }
    }

    fun withEvents(block: EventService.() -> Unit) = withCalendarAuth {
        try {
            val calendar = GoogleCalendarEventService(getCalendar()!!, timezone, language, this@Bot).configure()
            if (!sent) { // calendar listener not called
                block(calendar)
            }
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == HttpStatus.UNAUTHORIZED.value()) {
                needsLoginCalendar(this)
            } else throw e
        }
    }

    fun withTasksEvents(block: (TaskService, EventService) -> Unit) = withTasks {
        val taskService = this
        withEvents {
            val eventService = this
            block(taskService, eventService)
        }
    }

    fun login() = withTasks {
        loginIfTokenSuccess(true)
    }

    private fun loginIfTokenSuccess(success: Boolean) {
        withCalendarAuth {
            if (!success) {
                needsLogin()
                clear()
            }
        }
        send()
    }

    fun greetings() {
        onNeedLogin = { addMessage(S.GREETINGS) }
        login()
    }

    private fun requireToken(type: TokenType, block: (String) -> Unit) {
        val token = tokens[type]
        lastRequiredToken = type
        if (token == null) {
            needsLogin()
        } else {
            block(token)
        }
    }

    fun needsLogin(type: TokenType = lastRequiredToken!!, withMessages: Boolean = true) {
        onNeedLogin()
        logger.info("Needs login $type")
        val id = when (type) {
            TokenType.TRELLO -> S.LOGIN_TASKS
            TokenType.TOGGL -> S.LOGIN_TIMER
        }
        if (withMessages) {
            val messages = get(id).split('\n')
            addMessage(messages[0])
            addText(type.authUrl)
            addMessage(messages[1])
        }
        error = LoginError(type)
        tokens[type] = ""
        action.fillUserTokens(tokens)
    }

    fun needsLogin(id: S, authUrl: String) {
        onNeedLogin()
        addMessage(id)
        addText(authUrl)
    }

    fun logout(withMessages: Boolean = true) {
        requireToken(TokenType.TRELLO) { GoogleApiAuthorization(it).clear() }
        needsLogin(TokenType.TRELLO, false)
        resetContext(USER_CONTEXT)
        if (withMessages) {
            addMessage(S.LOGOUT)
        }
    }

    private fun send() {
        if (sent) return
        if (error == null) {
            action.completeTokensFilling(tokens)
        }
        action.fillAndSend(messages.firstOrNull()?.textToSpeech,
                mutableMapOf("source" to action.source,
                        "messages" to messages,
                        "language" to language,
                        "timezone" to timezone.id,
                        "tokens" to tokens.map { Token(it.key, it.value) })
                        .apply { if (error != null) this["error"] = error })
        sent = true
    }

    fun isContext(contextName: String) = action.getContext(contextName) != null

    fun setContext(contextName: String, lifespan: Int = 1) = action.setContext(contextName, lifespan)

    fun resetContext(contextName: String) = action.setContext(contextName, 0)

    fun setArgument(id: String, value: String?, contextName: String = USER_CONTEXT) = action.setArgument(id, value, contextName)

    fun getParam(param: String, context: String? = null, contextParam: String = param): Any? {
        return action.getArgument(param) ?: // try to get from parameters
            context?.let { action.getContextArgument(it, contextParam)?.value } // otherwise try to get from context
    }

    fun getString(param: String, context: String? = null, contextParam: String = param): String? {
        val retrieved = getParam(param, context, contextParam)?.toString()
        return if (retrieved.isNullOrBlank()) null else retrieved
    }

    fun getNestedString(keys: Pair<String, String>, context: String? = null): String? {
        val param = getParam(keys.first, context) ?: return null
        return if (param is Map<*,*>) param[keys.second]?.toString() else param.toString()
    }

    fun getDouble(param: String, context: String? = null, contextParam: String = param): Double = getString(param, context, contextParam)?.toDouble() ?: 0.toDouble()

    private fun String?.parseDate(): LocalDate? = orEmpty()
            .letIf(String::isNotEmpty, { calendarService.parse(it) }, { null })

    fun getDate(param: String, implicit: Boolean = true, context: String? = null, contextParam: String = param): LocalDate? {
        val date = getString(param, context, contextParam).parseDate()
        val dateOriginal = getString("$param-original")
        return if (implicit && date != null) {
            calendarService.implicitDate(ZonedDateTime.now(timezone), date, dateOriginal, language)
        } else date
    }

    private fun String?.parseTime(): LocalTime? = orEmpty()
            .letIf(String::isNotEmpty, { clockService.parse(it) }, { null })

    fun getTime(param: String, implicit: Boolean = true, context: String? = null, contextParam: String = param): LocalTime? {
        val time = getString(param, context, contextParam).parseTime()
        val timeOriginal = getString("$param-original")
        return if (implicit && time != null) {
            calendarService.implicitTime(ZonedDateTime.now(timezone), time, timeOriginal, language)
        } else time
    }

    private fun Pair<LocalDate?, LocalTime?>.parseDateTime(defaultDate: LocalDate? = null, defaultTime: LocalTime? = null): LocalDateTime? {
        val (date, time) = this
        if (date == null && time == null && defaultDate == null && defaultTime == null) return null
        return (date ?: defaultDate ?: calendarService.today(timezone)).atTime(time ?: defaultTime ?: LocalTime.MIDNIGHT.withSecond(0))
    }

    fun getDateTime(dateParam: String, timeParam: String, defaultDate: LocalDate? = null, defaultTime: LocalTime? = null, implicit: Boolean = true, context: String? = null): ZonedDateTime? {
        return (getDate(dateParam, implicit, context) to getTime(timeParam, implicit, context)).parseDateTime(defaultDate, defaultTime)?.atZone(timezone)
    }

    fun getTimePeriod(startParam: String, endParam: String, timePeriod: String? = null, implicit: Boolean = true, context: String? = null): TimePeriod? {
        val period = timePeriod?.let { getString(it, context) }
        val start = getTime(startParam, implicit, context)
        val end = getTime(endParam, implicit, context)
        return if (start != null && end != null) {
            TimePeriod(start, end)
        } else if (period != null) {
            TimePeriod.parse(period)
        } else null
    }

    private fun Any?.parseDatePeriod(): DatePeriod? {
        val arg = this ?: return null
        return if (arg is Map<*,*>) {
            val start = arg["startDate"]?.toString() ?: return null
            val end = arg["endDate"]?.toString() ?: return null
            DatePeriod(start, end, calendarService)
        } else DatePeriod.parse(arg.toString(), calendarService)
    }

    fun getDatePeriod(param: String, context: String? = null, contextParam: String = param): DatePeriod? = getParam(param, context, contextParam).parseDatePeriod()

    private fun parseDuration(param: String, context: String? = null, contextParam: String = param): Duration? {
        return getString(param, context, contextParam)?.let { gson.fromJson(it, JsonDuration::class.java).toDuration(language) }
    }

    private fun getDurationFrom(first: Duration?, second: Duration?): Duration? {
        var duration = first
        var duration2 = second
        if (duration2 != null && duration == null) {
            duration = duration2
            duration2 = null
        }
        if (duration != null && duration2 != null) {
            duration = duration.plus(duration2)
        }
        return duration
    }

    fun getDuration(param: String, param2: String = param + "2", context: String? = null): Duration? = getDurationFrom(parseDuration(param, context), parseDuration(param2, context))

    fun isCancelled(s: String?): Boolean {
        if (s.equals(i18n[S.CANCEL, language], true)) {
            addMessage(S.CANCELLED)
            return true
        }
        return false
    }

    fun isOrWas(date: LocalDate) = get(if (date.isBefore(calendarService.today(timezone))) S.WAS else S.IS)

    operator fun get(id: S): String = i18n[id, language]

    override fun onNewBoard(board: Board) {
        addMessage(S.NEW_BOARD)
        board.getAccessUrl()?.let { addText(it) }
    }

    override fun onNewCalendar() {
        val message = get(S.NEW_CALENDAR)
        addMessage(textToSpeech = message.replace("\$url\n", ""),
                   displayText = message.replace("\$url", "https://calendar.google.com/calendar/"))
        send()
    }

    companion object Parser {

        val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }

        fun handleRequest(req: HttpServletRequest, res: HttpServletResponse, gson: Gson,
                          intentMapper: Map<String, Handler>, calendarService: CalendarService, clockService: ClockService) {
            val action = DialogflowAction(req, res, gson).app
            logger.info("Query '${action.query}'")
            var bot: Bot? = null
            try {
                val intent = action.getIntent()
                val handler = intentMapper[intent]
                if (handler != null) {
                    logger.info("Intent $intent")
                    val language = parseLanguage(action.request.body.lang)
                    logger.info("Language $language")
                    val originalRequest = JsonParser().parse(req.body).asJsonObject?.getAsJsonObject("originalRequest")
                    val zenkaiData = originalRequest?.getAsJsonObject("data")?.getAsJsonObject("zenkai")
                    val data: ZenkaiRequestData? = gson.fromJson(zenkaiData, ZenkaiRequestData::class.java)
                    logger.info("Data $data")
                    val timezone = Try {
                        data?.timezone?.toZoneIdOrThrow() ?: DEFAULT_TIME_ZONE
                    }.getOrElse { DEFAULT_TIME_ZONE }
                    logger.info("Time Zone $timezone")
                    val tokens = mutableMapOf<TokenType, String>()
                    fillContextTokens(action.request.body.result.resolvedQuery, action, tokens)
                    fillDataTokens(data?.tokens, tokens, language)
                    action.fillUserTokens(tokens)
                    bot = Bot(language, timezone, req.requestURL.toString(), action, tokens, calendarService, clockService, gson)
                    handler(bot)
                    bot.send()
                }
            } catch (e: IllegalArgumentException) {
                action.badRequest(e)
            } catch (e: HttpClientErrorException) {
                if (bot != null && e.statusCode == HttpStatus.UNAUTHORIZED) {
                    bot.loginIfTokenSuccess(false)
                } else action.serviceUnavailable(e)
            } catch (e: Exception) {
                var message = e.message
                if (message != null && "timeout" in message) {
                    message = i18n[S.TIMEOUT, bot?.language ?: DEFAULT_LANGUAGE]
                }
                action.serviceUnavailable(e, message)
            }
        }

        private fun fillContextTokens(query: String, action: DialogflowApp, tokens: MutableMap<TokenType, String>) {
            TokenType.values().forEach {
                if (it !in tokens) {
                    var token: String
                    if (query.matches(it.regex)) {
                        token = query
                    } else {
                        token = action.getArgument(it.param)?.toString().orEmpty()
                        if (token.isBlank()) {
                            token = action.getContextArgument(USER_CONTEXT, it.param)?.value?.toString().orEmpty()
                        }
                    }
                    if (token.isNotBlank()) {
                        tokens[it] = token
                    }
                }
            }
        }

        private fun fillDataTokens(dataTokens: List<Token>?, tokens: MutableMap<TokenType, String>, language: String) {
            dataTokens?.forEach {
                if (it.token.isNotNullOrBlank() && it.type != null) {
                    val tokenType = TokenType.valueOf(it.type.toUpperCase(language.toLocale()))
                    tokens.putIfAbsent(tokenType, it.token!!)
                }
            }
        }

        private fun DialogflowApp.fillUserTokens(tokens: Map<TokenType, String>) {
            if (!tokens.isEmpty()) {
                val contextTokens = mutableMapOf<String, Any>()
                TokenType.values().forEach { contextTokens[it.param] = tokens[it] ?: "" }
                setContext(USER_CONTEXT, 100, contextTokens)
            }
        }

        private fun DialogflowApp.completeTokensFilling(tokens: Map<TokenType, String>) {
            with (request.body.result) {
                if (actionIncomplete) {
                    TokenType.values().forEach {
                        if ((tokens[it] ?: "").isNotBlank()) {
                            val intentContext = (metadata?.intentName ?: action).replace('.', '_')
                            setContext("${intentContext}_dialog_context", 0)
                            setContext("${intentContext}_dialog_params_${it.param}", 0)
                            metadata?.intentId?.let {
                                setContext("${it}_id_dialog_context", 0)
                            }
                        }
                    }
                }
            }
        }

        private fun parseLanguage(language: String?): String {
            checkMissing(language, "language")
            val langCode = language!!.split('-')[0]
            return if (langCode !in i18n) {
                logger.warn("Language $langCode not supported, default to $DEFAULT_LANGUAGE")
                DEFAULT_LANGUAGE
            } else langCode
        }

        @Throws(IllegalArgumentException::class)
        private fun <T: Any, R> checkThrowing(obj: T?, argument: String, potentiallyThrowing: T.() -> R): R {
            val notNull = checkMissing(obj, argument)
            try {
                return potentiallyThrowing(notNull)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid $argument")
            }
        }

        @Throws(IllegalArgumentException::class)
        private fun <T: Any> checkInvalid(obj: T?, argument: String, valid: T.() -> Boolean): T {
            require(checkMissing(obj, argument).valid()) { "Invalid $argument" }
            return obj!!
        }

        @Throws(IllegalArgumentException::class)
        private fun <T: Any> checkMissing(obj: T?, argument: String): T {
            requireNotNull(obj) { "Missing $argument" }
            return obj!!
        }

    }
}

val HttpServletRequest.body get() = inputStream.reader().readText()

val DialogflowApp.source get() = request.body.originalRequest?.source ?: request.body.result.source ?: "zenkai"

val DialogflowApp.query get() = request.body.result.resolvedQuery

val logger get() = Bot.logger

fun DialogflowApp.setArgument(id: String, value: String?, contextName: String = USER_CONTEXT) {
    val params = getContext(contextName)?.parameters ?: mutableMapOf<String, Any>()
    if (value == null) {
        params.remove(id)
    } else {
        params[id] = value
    }
    setContext(contextName, lifespan=100, parameters=params)
}

private fun DialogflowApp.error(error: BotError, e: Exception) {
    logError(error, e)
    fillAndSend(error.message, mapOf("error" to error))
}

private fun logError(error: BotError?, e: Exception) {
    if (error != null && error.status != HttpStatus.UNAUTHORIZED.value()) {
        logger.error(error.message, e)
    }
}

private fun DialogflowApp.badRequest(e: Exception) {
    error(BadRequestError(e), e)
}

private fun DialogflowApp.serviceUnavailable(e: Exception, message: String? = e.message) {
    error(BotError(message, HttpStatus.SERVICE_UNAVAILABLE), e)
}

private fun DialogflowApp.fillAndSend(speech: String?, data: Map<String, Any>) {
    data { this["zenkai"] = data }
    val textToSpeech = speech.orEmpty()
    logger.info("Send '$textToSpeech' with ${(data["messages"] as? List<*>)?.size} messages")
    tell(textToSpeech)
}

private data class ZenkaiRequestData(val timezone: String?, val tokens: List<Token>?)