package ai.zenkai.zenkai.services.clock

import ai.zenkai.zenkai.i18n.toLocale
import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.zone.ZoneRulesException
import java.util.*

const val DEFAULT_TIMEZONE_ID = "UTC"
val DEFAULT_TIME_ZONE by lazy { ZoneId.of(DEFAULT_TIMEZONE_ID) }

@Service
class ClockService {

    private val formatter12 by lazy { DateTimeFormatter.ofPattern("h:mm a") }
    private val formatter24 by lazy { DateTimeFormatter.ofPattern("H:mm") }

    fun now(timezone: ZoneId): LocalTime = LocalTime.now(timezone)

    fun format(time: LocalTime, locale: Locale) = pretty12(time, locale)
    fun format(time: LocalTime, language: String) = format(time, language.toLocale())

    @Throws(DateTimeException::class, ZoneRulesException::class)
    fun formatNow(language: String, timezone: String) = format(now(timezone.toZoneIdOrThrow()), language)

    fun pretty12(time: LocalTime, locale: Locale): String = formatter12.withLocale(locale).format(time)
    fun pretty12(time: LocalTime, language: String): String = pretty12(time, language.toLocale())

    fun pretty24(time: LocalTime, locale: Locale): String = formatter24.withLocale(locale).format(time)
    fun pretty24(time: LocalTime, language: String): String = pretty24(time, language.toLocale())

}

@Throws(DateTimeException::class, ZoneRulesException::class)
fun String.toZoneIdOrThrow(): ZoneId = ZoneId.of(this)