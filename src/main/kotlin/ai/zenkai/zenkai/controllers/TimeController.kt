package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.exceptions.BadRequestError
import ai.zenkai.zenkai.exceptions.multicatch
import ai.zenkai.zenkai.exceptions.ok
import ai.zenkai.zenkai.i18n.DEFAULT_LANGUAGE
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.clock.DEFAULT_TIMEZONE_ID
import ai.zenkai.zenkai.services.clock.Time
import ai.zenkai.zenkai.services.clock.isSingleHour
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.DateTimeException
import java.time.zone.ZoneRulesException

@RestController
class TimeController(private val clockService: ClockService): ActionController {

    override val actionMap: Map<String, Handler> = mapOf(
            "time.get" to { b -> b.clock() }
    )

    @GetMapping("/time", produces = ["application/json"])
    fun time(@RequestParam(value = "timezone", defaultValue = DEFAULT_TIMEZONE_ID) timezone: String,
             @RequestParam(value = "language", defaultValue = DEFAULT_LANGUAGE) language: String): ResponseEntity<*> {
        // Only for testing purpose
        return try {
            ok(Time(clockService.formatNow(language, timezone), timezone))
        } catch (e: Exception) {
            e.multicatch(DateTimeException::class, ZoneRulesException::class) {
                BadRequestError("Invalid Timezone: $timezone").getResponseEntity()
            }
        }
    }

    fun Bot.clock() {
        val time = clockService.now(timezone)
        val prefix = get(if (time.isSingleHour()) S.CURRENT_TIME_SINGLE else S.CURRENT_TIME)
        val formattedTime = clockService.pretty12(time, language)
        val speech = "${prefix.capitalize()} $formattedTime".trim()
        addMessage(speech, formattedTime)
    }

}