package ai.zenkai.zenkai.services.calendar

import me.carleslc.kotlin.extensions.time.toDate
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

@Service
class CalendarService {

    private val prettyTime by lazy { PrettyTime() }
    private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

    fun pretty(date: LocalDateTime, zoneId: ZoneId, language: String): String {
        return "${prettyDuration(date, zoneId)} (${prettyDate(date, language)})"
    }

    fun prettyDuration(date: LocalDateTime, zoneId: ZoneId): String = prettyTime.format(date.toDate(zoneId))

    fun prettyDate(date: LocalDateTime, language: String) : String {
        return formatter.withLocale(language.toLocale()).format(date)
    }

}

fun String.toLocale() = Locale(this)