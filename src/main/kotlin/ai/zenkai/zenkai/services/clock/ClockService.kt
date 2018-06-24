package ai.zenkai.zenkai.services.clock

import ai.zenkai.zenkai.i18n.toLocale
import ai.zenkai.zenkai.remove
import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneRulesException
import java.util.*

const val DEFAULT_TIMEZONE_ID = "UTC"
val DEFAULT_TIME_ZONE by lazy { ZoneId.of(DEFAULT_TIMEZONE_ID) }

@Service
class ClockService {

    private val formatter12 by lazy { DateTimeFormatter.ofPattern("h:mm a") }
    private val formatter24 by lazy { DateTimeFormatter.ofPattern("H:mm") }

    private val durationFormat by lazy { """(?=.*[dhms])(?:(\d+d\s*)?(\d+h\s*)?(\d+m\s*)?(\d+s\s*)?)""".toRegex() }

    fun now(zoneId: ZoneId = DEFAULT_TIME_ZONE): LocalTime = LocalTime.now(zoneId)

    fun parse(time: String): LocalTime = LocalTime.parse(time)

    @Throws(DateTimeException::class, ZoneRulesException::class)
    fun formatNow(language: String, timezone: String) = pretty12(now(timezone.toZoneIdOrThrow()), language)

    fun pretty12(time: LocalTime, locale: Locale): String = formatter12.withLocale(locale).format(time)
    fun pretty12(time: LocalTime, language: String): String = pretty12(time, language.toLocale())

    fun pretty24(time: LocalTime, locale: Locale): String = formatter24.withLocale(locale).format(time)
    fun pretty24(time: LocalTime, language: String): String = pretty24(time, language.toLocale())

    fun removeDuration(s: String) = s.remove(durationFormat).trim()

    fun extractDuration(s: String?): Duration {
        fun parseDuration(groups: MatchGroupCollection): Duration {
            fun groupAmount(i: Int): Long = groups[i]?.value?.takeWhile(Char::isDigit)?.toLong() ?: 0L
            val days = groupAmount(1)
            val hours = groupAmount(2)
            val minutes = groupAmount(3)
            val seconds = groupAmount(4)
            val totalSeconds = days * 24 * 3600 + hours * 3600 + minutes * 60 + seconds
            return Duration.ofSeconds(totalSeconds)
        }
        return s?.let { durationFormat.findAll(it).lastOrNull()?.let { parseDuration(it.groups) } } ?: Duration.ZERO
    }

}

@Throws(DateTimeException::class, ZoneRulesException::class)
fun String.toZoneIdOrThrow(): ZoneId = ZoneId.of(this)

fun LocalTime.isSingleHour() = hour == 1 || hour == 13