package ai.zenkai.zenkai.model

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
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.clock.DEFAULT_TIME_ZONE
import ai.zenkai.zenkai.services.clock.toZoneIdOrThrow
import ai.zenkai.zenkai.services.tasks.TasksListener
import ai.zenkai.zenkai.services.tasks.TrelloTaskService
import ai.zenkai.zenkai.services.tasks.trello.Board
import arrow.data.Try
import arrow.data.getOrElse
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

const val USER_CONTEXT: String = "user-logged-in"

data class Bot(val language: String,
               val timezone: ZoneId,
               private val action: DialogflowApp,
               private val tokens: MutableMap<TokenType, String>,
               private val calendarService: CalendarService,
               private val clockService: ClockService,
               private var error: BotError? = null) : TasksListener {

    private val messages by lazy { mutableListOf<SimpleResponse>() }

    val query get() = action.query

    val locale by lazy { language.toLocale() }

    var lastRequiredToken: TokenType? = null

    fun tell(message: String) {
        addMessage(message)
        send()
    }

    fun tell(id: S) = tell(get(id))

    fun tell(textToSpeech: String? = null, displayText: String? = null) {
        if (addMessage(textToSpeech, displayText)) {
            send()
        }
    }

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

    fun addMessages(s: String) {
        s.split('\n').forEach { addMessage(it) }
    }

    fun addMessages(id: S) = addMessages(get(id))

    fun addTask(task: Task) {
        with(task) {
            addMessage(getSpeech(language, timezone, calendarService), getDisplayText(language, timezone, calendarService))
        }
    }

    fun <T> fill(obj: T?, default: String, fill: T.() -> String) {
        tell(if (obj.isNull()) default else fill(obj!!))
    }

    fun withTrello(block: TrelloTaskService.() -> Unit) = requireToken(TokenType.TRELLO) {
        block(TrelloTaskService(it, this, timezone))
    }

    fun containsToken(type: TokenType) = tokens[type] != null

    private fun requireToken(type: TokenType, block: (String) -> Unit) {
        val token = tokens[type]
        lastRequiredToken = type
        if (token == null) {
            needsLogin()
        } else {
            block(token)
        }
    }

    fun needsLogin(id: S = S.LOGIN_TOKEN, type: TokenType = lastRequiredToken!!) {
        val messages = get(id).replace("\$type", type.toString()).split('\n')
        addMessage(messages[0])
        addText(type.authUrl)
        addMessage(messages[1])
        error = LoginError(type)
        tokens[type] = ""
        action.fillUserTokens(tokens)
        send()
    }

    fun send(ask: Boolean = false) {
        if (error == null) {
            // TODO: Redirect with followupEvent instead clearing slot filling contexts
            action.completeTokensFilling(tokens)
        }
        action.fillAndSend(messages.firstOrNull()?.textToSpeech,
                mutableMapOf("source" to action.source,
                        "messages" to messages,
                        "language" to language,
                        "timezone" to timezone.id,
                        "tokens" to tokens.map { Token(it.key, it.value) })
                        .apply { if (error != null) this["error"] = error }, ask)
    }

    fun getParam(param: String): Any? = action.getArgument(param)

    fun getParamWithContext(param: String, context: String = USER_CONTEXT): Any? {
        return getParam(param) ?: // try to get from parameters
            action.getContextArgument(context, param)?.value // else try to get from context
    }

    fun getStringWithContext(param: String, context: String = USER_CONTEXT): String? {
        val retrieved = getParamWithContext(param, context)?.toString()
        return if (retrieved.isNullOrBlank()) null else retrieved
    }

    fun getString(param: String): String? {
        val retrieved = getParam(param)?.toString()
        return if (retrieved.isNullOrBlank()) null else retrieved
    }

    fun getNestedString(keys: Pair<String, String>): String? {
        val param = getParam(keys.first) ?: return null
        return if (param is Map<*,*>) param[keys.second]?.toString() else param.toString()
    }

    fun getDoubleWithContext(param: String, context: String = USER_CONTEXT): Double {
        return getStringWithContext(param, context)?.toDouble() ?: 0.toDouble()
    }

    fun getDouble(param: String): Double = getString(param)?.toDouble() ?: 0.toDouble()

    private fun String?.parseDate(): LocalDate? = orEmpty()
            .letIf(String::isNotEmpty, { calendarService.parse(it) }, { null })

    fun getDate(param: String): LocalDate? = getString(param).parseDate()

    fun getDateWithContext(param: String, context: String = USER_CONTEXT): LocalDate? {
        return getStringWithContext(param, context).parseDate()
    }

    private fun String?.parseTime(): LocalTime? = orEmpty()
            .letIf(String::isNotEmpty, { clockService.parse(it) }, { null })

    fun getTime(param: String): LocalTime? = getString(param).parseTime()

    fun getTimeWithContext(param: String, context: String = USER_CONTEXT): LocalTime? {
        return getStringWithContext(param, context).parseTime()
    }

    private fun Pair<LocalDate?, LocalTime?>.parseDateTime(): LocalDateTime? {
        val (date, time) = this
        if (date == null && time == null) return null
        return (date ?: calendarService.today(timezone)).atTime(time ?: LocalTime.MIDNIGHT.withSecond(0))
    }

    fun getDateTime(dateParam: String, timeParam: String): LocalDateTime? {
        return (getDate(dateParam) to getTime(timeParam)).parseDateTime()
    }

    fun getDateTimeWithContext(dateParam: String, timeParam: String, context: String = USER_CONTEXT): LocalDateTime? {
        return (getDateWithContext(dateParam, context) to getTimeWithContext(timeParam, context)).parseDateTime()
    }

    private fun Any?.parseDatePeriod(): DatePeriod? {
        val arg = this ?: return null
        return if (arg is Map<*,*>) {
            val start = arg["startDate"]?.toString() ?: return null
            val end = arg["endDate"]?.toString() ?: return null
            DatePeriod(start, end, calendarService)
        } else DatePeriod.parse(arg.toString(), calendarService)
    }

    fun getDatePeriod(param: String): DatePeriod? = getParam(param).parseDatePeriod()

    fun getDatePeriodWithContext(param: String, context: String = USER_CONTEXT): DatePeriod? {
        return getStringWithContext(param, context).parseDatePeriod()
    }

    fun isOrWas(date: LocalDate) = get(if (date.isBefore(calendarService.today(timezone))) S.WAS else S.IS)

    operator fun get(id: S): String = i18n[id, language]

    override fun onNewBoard(board: Board) {
        addMessage(get(S.NEW_BOARD))
        addText(board.shortUrl!!)
    }

    companion object Parser {

        val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }

        fun handleRequest(req: HttpServletRequest, res: HttpServletResponse, gson: Gson,
                          intentMapper: Map<String, Handler>, calendarService: CalendarService, clockService: ClockService) {
            val action = DialogflowAction(req, res, gson).app
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
                    logger.info("Tokens $tokens")
                    bot = Bot(language, timezone, action, tokens, calendarService, clockService)
                    handler(bot)
                }
            } catch (e: IllegalAccessException) {
                action.badRequest(e)
            } catch (e: HttpClientErrorException) {
                if (bot != null && e.statusCode == HttpStatus.UNAUTHORIZED) {
                    bot.needsLogin()
                } else action.serviceUnavailable(e)
            } catch (e: Exception) {
                action.serviceUnavailable(e)
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
                logger.info("Filled User Tokens")
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

private val logger get() = Bot.logger

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

private fun DialogflowApp.serviceUnavailable(e: Exception) {
    error(BotError(e.message, HttpStatus.SERVICE_UNAVAILABLE), e)
}

private fun DialogflowApp.fillAndSend(speech: String?, data: Map<String, Any>, ask: Boolean = false) {
    data { this["zenkai"] = data }
    val textToSpeech = speech.orEmpty()
    logger.info("Send '$textToSpeech' with data $data")
    if (ask) ask(textToSpeech) else tell(textToSpeech)
}

private data class ZenkaiRequestData(val timezone: String?, val tokens: List<Token>?)