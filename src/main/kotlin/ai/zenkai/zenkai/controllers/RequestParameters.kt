package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.S
import ai.zenkai.zenkai.exceptions.InvalidArgumentException
import ai.zenkai.zenkai.exceptions.MissingRequiredArgumentException
import ai.zenkai.zenkai.i18n
import ai.zenkai.zenkai.services.clock.toZoneIdOrThrow
import com.tmsdurham.actions.DialogflowApp
import org.funktionale.tries.Try
import org.slf4j.LoggerFactory
import java.time.ZoneId

data class RequestParameters(val action: DialogflowApp,
                             val language: String,
                             val timezone: ZoneId,
                             val trelloToken: String?) {

    operator fun get(id: S) = i18n[id, language]

    companion object Factory {

        private val logger = LoggerFactory.getLogger(RequestParameters::class.java)

        @Throws(InvalidArgumentException::class, MissingRequiredArgumentException::class)
        fun from(action: DialogflowApp): RequestParameters {
            var language = action.request.body.lang ?: missing("language")
            if (language !in i18n) {
                val default = i18n.default()
                logger.warn("Language $language not supported, default to $default")
                language = default
            }
            val inputs = action.request.body.originalRequest?.data?.inputs ?: missing("inputs")
            if (inputs.isNotEmpty()) {
                val args = inputs[0].arguments ?: missing("args")
                var timezone: String? = null
                var trelloToken: String? = null
                for (arg in args) {
                    when (arg.name) {
                        "timezone" -> timezone = arg.textValue
                        "trello-token" -> trelloToken = arg.textValue
                    }
                }
                if (timezone == null) missing("timezone")
                val zoneId = Try { timezone.toZoneIdOrThrow() }.getOrElse { invalid("timezone") }
                return RequestParameters(action, language, zoneId, trelloToken)
            }
            missing("inputs")
        }

        @Throws(InvalidArgumentException::class)
        private fun invalid(argument: String): Nothing = throw InvalidArgumentException(argument)

        @Throws(MissingRequiredArgumentException::class)
        private fun missing(argument: String): Nothing = throw MissingRequiredArgumentException(argument)
    }

}