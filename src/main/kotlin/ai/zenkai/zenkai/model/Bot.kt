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
import ai.zenkai.zenkai.services.clock.DEFAULT_TIME_ZONE
import ai.zenkai.zenkai.services.clock.toZoneIdOrThrow
import arrow.data.Try
import arrow.data.getOrElse
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tmsdurham.actions.DialogflowApp
import com.tmsdurham.actions.SimpleResponse
import main.java.com.tmsdurham.dialogflow.sample.DialogflowAction
import me.carleslc.kotlin.extensions.standard.isNotNull
import me.carleslc.kotlin.extensions.standard.isNull
import me.carleslc.kotlin.extensions.standard.letIf
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import wu.seal.jvm.kotlinreflecttools.changePropertyValue
import java.time.LocalDate
import java.time.ZoneId
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

typealias Handler = (Bot) -> Unit

const val USER_CONTEXT: String = "user-logged-in"

data class Bot(val language: String,
               val timezone: ZoneId,
               private val action: DialogflowApp,
               private val tokens: Map<TokenType, String>,
               private val calendarService: CalendarService,
               private var error: BotError? = null) {

    private val messages by lazy { mutableListOf<SimpleResponse>() }

    val query by lazy { action.request.body.result.resolvedQuery }

    val accessToken by lazy { action.request.body.originalRequest?.data?.user?.accessToken }

    val locale by lazy { language.toLocale() }

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

    fun addMessage(s: String) = addMessage(s, s)

    fun addMessages(s: String) {
        s.split('\n').forEach { addMessage(it) }
    }

    fun addSpeech(speech: String) = addMessage(textToSpeech = speech)

    fun addText(text: String) = addMessage(displayText = text)

    fun addMessage(textToSpeech: String? = null, displayText: String? = null): Boolean {
        if (textToSpeech.isNotNullOrBlank() || displayText.isNotNullOrBlank()) {
            messages.add(SimpleResponse(textToSpeech = textToSpeech.nullIfBlank(), displayText = displayText.nullIfBlank()))
            return true
        }
        return false
    }

    fun addTask(task: Task) {
        with(task) {
            addMessage(getSpeech(language, timezone, calendarService), getDisplayText(language, timezone, calendarService))
        }
    }

    fun <T> fill(obj: T?, default: String, fill: T.() -> String) {
        tell(if (obj.isNull()) default else fill(obj!!))
    }

    fun requireToken(type: TokenType, block: (String) -> Unit) {
        val token = tokens[type]
        if (token == null) {
            needsLogin(type)
            send()
        }
        else block(token)
    }

    private fun needsLogin(type: TokenType) {
        logger.info("Needs login $type")
        error = LoginError(type)
        val messages = get(S.LOGIN_TOKEN).replace("\$type", type.toString()).split('\n')
        addMessage(messages[0])
        addText(type.authParams)
        addMessage(messages[1])
    }

    private fun badRequest(message: String? = null) {
        error = BadRequestError(message)
        logError(error, logger)
    }

    fun send(ask: Boolean = false) = action.fillAndSend(messages.firstOrNull()?.textToSpeech,
            mutableMapOf("source" to action.source,
                    "messages" to messages,
                    "language" to language,
                    "timezone" to timezone.id,
                    "tokens" to tokens.map { Token(it.key.name, it.value) })
                    .apply { if (error != null) this["error"] = error }, ask)

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

    fun getDateOrNullWithContext(param: String, context: String = USER_CONTEXT): LocalDate? {
        return getStringWithContext(param, context).orEmpty()
                .letIf(String::isNotEmpty, { calendarService.parse(it) }, { null })
    }

    fun getDateOrNull(param: String): LocalDate? = getString(param).orEmpty()
            .letIf(String::isNotEmpty, { calendarService.parse(it) }, { null })

    fun getDateWithContext(param: String, context: String = USER_CONTEXT): LocalDate? {
        return getDateOrNullWithContext(param, context)
    }

    fun getDate(param: String): LocalDate? = getDateOrNull(param)

    fun getDatePeriodWithContext(param: String, context: String = USER_CONTEXT): DatePeriod? {
        return getStringWithContext(param, context)?.let { DatePeriod.parse(it, calendarService) }
    }

    fun getDatePeriod(param: String): DatePeriod? {
        val arg = getParam(param) ?: return null
        return if (arg is Map<*,*>) {
            val start = arg["startDate"]?.toString() ?: return null
            val end = arg["endDate"]?.toString() ?: return null
            DatePeriod(start, end, calendarService)
        } else DatePeriod.parse(arg.toString(), calendarService)
    }

    fun isOrWas(date: LocalDate) = get(if (date.isBefore(calendarService.today(timezone))) S.WAS else S.IS)

    operator fun get(id: S): String = i18n[id, language]

    companion object Parser {

        val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }

        fun handleRequest(req: HttpServletRequest, res: HttpServletResponse, gson: Gson,
                          intentMapper: Map<String, Handler>, calendarService: CalendarService) {
            val action = DialogflowAction(req, res, gson).app
            try {
                val handler = intentMapper[action.getIntent()]
                if (handler != null) {
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
                    fillDataTokens(data?.tokens, tokens)
                    fillContextTokens(action, tokens)
                    tokens.forEach { action.fillParameter(it.key.param, it.value) }
                    logger.info("Tokens $tokens")
                    handler(Bot(language, timezone, action, tokens, calendarService))
                }
            } catch (e: IllegalArgumentException) {
                action.error(BadRequestError(e), logger)
            }
        }

        private fun fillDataTokens(dataTokens: List<Token>?, tokens: MutableMap<TokenType, String>) {
            dataTokens?.forEach {
                if (it.token.isNotNullOrBlank() && it.type != null) {
                    tokens[TokenType.valueOf(it.type)] = it.token!!
                }
            }
        }

        private fun fillContextTokens(action: DialogflowApp, tokens: MutableMap<TokenType, String>) {
            TokenType.values().forEach {
                if (it !in tokens) {
                    val tokenParam = action.getContextArgument(USER_CONTEXT, it.param)?.value
                    if (tokenParam != null) {
                        val token = tokenParam.toString()
                        if (token.isNotBlank()) {
                            tokens[it] = token
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

fun DialogflowApp.fillParameter(param: String, value: String) = with(request.body.result) {
    if (parameters.isNull()) {
        changePropertyValue("parameters", mutableMapOf<String, Any>())
    }
    parameters!![param] = value
}

private val logger get() = Bot.logger

private fun DialogflowApp.error(error: BotError, logger: Logger) {
    logError(error, logger)
    fillAndSend(error.message, mapOf("error" to error))
}

private fun logError(error: BotError?, logger: Logger) {
    if (error != null && error.status != HttpStatus.UNAUTHORIZED.value()) {
        logger.warn(error.message)
    }
}

private fun DialogflowApp.fillAndSend(speech: String?, data: Map<String, Any>, ask: Boolean = false) {
    data { this["zenkai"] = data }
    val textToSpeech = speech.orEmpty()
    logger.info("Send '$textToSpeech' with data $data")
    if (ask) ask(textToSpeech) else tell(textToSpeech)
}

private data class ZenkaiRequestData(val timezone: String?, val tokens: List<Token>?)

private data class Token(val type: String?, val token: String?)