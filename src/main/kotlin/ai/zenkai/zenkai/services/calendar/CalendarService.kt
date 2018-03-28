package ai.zenkai.zenkai.services.calendar

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.toLocale
import ai.zenkai.zenkai.services.clock.ClockService
import me.carleslc.kotlin.extensions.time.toDate
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.*

@Service
class CalendarService(private val clockService: ClockService) {

    private val prettyTime by lazy { PrettyTime() }
    private val simpleDateFormatter by lazy { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }
    private val dialogFlowDateFormatter by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    fun today(zoneId: ZoneId): LocalDate = LocalDate.now(zoneId)

    fun toDate(date: String, language: String): LocalDate {
        return LocalDate.parse(date, dialogFlowDateFormatter.withLocale(language.toLocale()))
    }

    fun formatDate(date: LocalDate, language: String): String = simpleDateFormatter.withLocale(language.toLocale()).format(date)

    fun prettyApproxDateTime(date: LocalDateTime, zoneId: ZoneId, language: String): String {
        return "${prettyApprox(date, zoneId, language)} (${prettyDateTime(date, language)})"
    }

    fun prettyDateTime(date: LocalDateTime, language: String): String {
        return "${prettyDate(date.toLocalDate(), language)}, ${clockService.pretty24(date.toLocalTime(), language)}"
    }

    fun prettyApprox(date: LocalDateTime, zoneId: ZoneId, language: String): String {
        return prettyTime(language).format(date.toDate(zoneId))
    }

    fun prettyDate(date: LocalDate, language: String) : String {
        return "${getDayOfWeek(date, language)} ${getDayOfMonth(date, language)}"
    }

    private fun withDateSuffix(dayOfMonth: Int, language: String): String {
        val suffix = if (language == "en") {
            if (dayOfMonth in 11..13) {
                "th"
            } else when (dayOfMonth % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        } else ""
        return "$dayOfMonth$suffix"
    }

    fun getDayOfMonth(date: LocalDate, language: String): String {
        return "${withDateSuffix(date.dayOfMonth, language)} ${i18n[S.OF, language]} ${date.month.getDisplayName(TextStyle.FULL, language.toLocale())}"
    }

    fun getDayOfWeek(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    fun getDayOfWeek(date: LocalDate, language: String): String = getDayOfWeek(date, language.toLocale())

    private fun prettyTime(locale: Locale) = prettyTime.apply { this.locale = locale }
    private fun prettyTime(language: String) = prettyTime(language.toLocale())

}