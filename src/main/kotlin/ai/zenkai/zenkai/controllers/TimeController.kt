package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.exceptions.badRequest
import ai.zenkai.zenkai.exceptions.multicatch
import ai.zenkai.zenkai.exceptions.ok
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.clock.DEFAULT_TIMEZONE_ID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.DateTimeException
import java.time.zone.ZoneRulesException

@RestController
class TimeController(private val clockService: ClockService) {

    @GetMapping("/time", produces = ["application/json"])
    fun time(@RequestParam(value = "timezone", defaultValue = DEFAULT_TIMEZONE_ID) timezone: String): ResponseEntity<*> {
        return try {
            ok(clockService.getCurrentTime(timezone))
        } catch (e: Exception) {
            e.multicatch(DateTimeException::class, ZoneRulesException::class) {
                badRequest("Invalid Timezone: $timezone")
            }
        }
    }

}
