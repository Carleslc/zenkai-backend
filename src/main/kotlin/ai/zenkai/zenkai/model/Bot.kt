package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.exceptions.BadRequestError
import ai.zenkai.zenkai.exceptions.BotError
import ai.zenkai.zenkai.exceptions.LoginError
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.nullIfBlank
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.clock.DEFAULT_TIME_ZONE
import ai.zenkai.zenkai.services.clock.toZoneIdOrThrow
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tmsdurham.actions.DialogflowApp
import com.tmsdurham.actions.SimpleResponse
import main.java.com.tmsdurham.dialogflow.sample.DialogflowAction
import me.carleslc.kotlin.extensions.standard.isNull
import me.carleslc.kotlin.extensions.standard.letIf
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.funktionale.tries.Try
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

    fun tell(message: String) {
        addMessage(message)
        send()
    }

    fun tell(textToSpeech: String? = null, displayText: String? = null) {
        if (addMessage(textToSpeech, displayText)) {
            send()
        }
    }

    fun addMessage(s: String) = addMessage(s, s)

    fun addSpeech(speech: String) = addMessage(textToSpeech = speech)

    fun addText(text: String) = addMessage(displayText = text)

    fun addMessage(textToSpeech: String? = null, displayText: String? = null): Boolean {
        if (textToSpeech.isNotNullOrBlank() || displayText.isNotNullOrBlank()) {
            messages.add(SimpleResponse(textToSpeech = textToSpeech.nullIfBlank(), displayText = displayText.nullIfBlank()))
            return true
        }
        return false
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
        addMessage("I cannot do that without access to your $type account, what is your token?")
        addText(type.authUrl)
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

    fun getParam(param: String, context: String = USER_CONTEXT): Any? {
        return action.getArgument(param) ?: // try to get from parameters
            action.getContextArgument(context, param)?.value // else try to get from context
    }

    fun getString(param: String, context: String = USER_CONTEXT): String? {
        return getParam(param, context)?.toString()
    }

    fun getNestedString(keys: Pair<String, String>, context: String = USER_CONTEXT): String? {
        val param = getParam(keys.first, context) ?: return null
        return if (param is Map<*,*>) param[keys.second]?.toString() else param.toString()
    }

    fun getDouble(param: String, context: String = USER_CONTEXT): Double = getString(param, context)?.toDouble() ?: 0.toDouble()

    fun getDate(param: String, context: String = USER_CONTEXT): LocalDate = getString(param, context).orEmpty()
            .letIf(String::isNotBlank, { calendarService.toDate(it, language) }, { calendarService.today(timezone) })

    fun isOrWas(date: LocalDate) = get(if (date.isBefore(calendarService.today(timezone))) S.WAS else S.IS)

    operator fun get(id: S): String = i18n[id, language]

    companion object Parser {

        val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }

        fun handleRequest(req: HttpServletRequest, res: HttpServletResponse, gson: Gson,
                          intentMapper: Map<String, Handler>, calendarService: CalendarService) {
            val action = DialogflowAction(req, res, gson).app
            try {
                logger.info("Handle Request")
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
                    val tokenParam = action.getContextArgument("user-logged-in", it.param)?.value
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
            return if (language!! !in i18n) {
                val default = i18n.default()
                logger.warn("Language $language not supported, default to $default")
                default
            } else language
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