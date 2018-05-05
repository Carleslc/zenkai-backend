package ai.zenkai.zenkai.services.calendar

import ai.zenkai.zenkai.cleanFormat
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.toLocale
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.words
import org.ocpsoft.prettytime.PrettyTime
import org.springframework.stereotype.Service
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@Service
class CalendarService(private val clockService: ClockService) {

    private val prettyTimeFormatter by lazy { PrettyTime() }
    private val dialogFlowDateFormatter by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    private val daysOfWeek by lazy { i18n.map { it to getDaysOfWeek(it) }.toMap() }
    private val months by lazy { i18n.map { it to getMonths(it) }.toMap() }

    fun today(zoneId: ZoneId): LocalDate = LocalDate.now(zoneId)

    fun parse(date: String): LocalDate {
        return LocalDate.parse(date, dialogFlowDateFormatter)
    }

    fun formatDate(date: LocalDate): String = dialogFlowDateFormatter.format(date)

    fun prettyApproxDateTime(date: LocalDateTime, zoneId: ZoneId, language: String): String {
        val offsetDate = date.atOffset(ZoneOffset.UTC).atZoneSameInstant(zoneId)
        return "${prettyApprox(offsetDate, language)} (${prettyDateTime(offsetDate.toLocalDateTime(), zoneId, language)})"
    }

    fun prettyDateTime(date: LocalDateTime, zoneId: ZoneId, language: String): String {
        return "${prettyDate(date.toLocalDate(), language)}, ${clockService.pretty24(date.toLocalTime(), language)}"
    }

    private fun prettyApprox(date: ZonedDateTime, language: String): String {
        return prettyTime(language).format(Date(date.toInstant().toEpochMilli()))
    }

    fun prettyApprox(date: LocalDateTime, zoneId: ZoneId, language: String): String {
        return prettyApprox(date.atOffset(ZoneOffset.UTC).atZoneSameInstant(zoneId), language)
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
        return "${withDateSuffix(date.dayOfMonth, language)} ${i18n[S.OF, language]} " +
                date.month.getDisplayName(TextStyle.FULL, language.toLocale())
    }

    fun getDayOfWeek(date: LocalDate, locale: Locale): String = date.dayOfWeek.displayName(locale)
    fun getDayOfWeek(date: LocalDate, language: String): String = getDayOfWeek(date, language.toLocale())

    fun isDayOfWeek(query: String, language: String): Boolean {
        val locale = language.toLocale()
        return query.cleanFormat(locale).words(locale).any { it in daysOfWeek[language]!! }
    }

    fun isDayOfMonth(query: String, language: String): Boolean {
        val locale = language.toLocale()
        return query.cleanFormat(locale).words(locale).any { it in months[language]!! }
    }

    fun isPast(query: String, language: String): Boolean {
        val lower = query.toLowerCase()
        return i18n[S.TOMORROW, language] !in lower && (i18n[S.WAS, language] in lower || i18n[S.PAST, language] in lower
                || i18n[S.AGO, language] in lower || i18n[S.PREVIOUS, language] in lower)
    }

    fun inPeriod(dayOfWeek: DayOfWeek, period: DatePeriod, refDate: LocalDate): LocalDate {
        val isPast = refDate >= period.end
        val startOfMove = (if (isPast) period.start else period.end).atStartOfWeek()
        val currentOffset = (dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()
        return startOfMove.plusDays(currentOffset)
    }

    private fun prettyTime(locale: Locale) = prettyTimeFormatter.apply { this.locale = locale }
    private fun prettyTime(language: String) = prettyTime(language.toLocale())

    private fun getDaysOfWeek(language: String): Set<String> {
        val locale = language.toLocale()
        return DayOfWeek.values().map { it.displayName(locale).cleanFormat(locale) }.toSet()
    }

    private fun getMonths(language: String): Set<String> {
        val locale = language.toLocale()
        return Month.values().map { it.displayName(locale).cleanFormat(locale) }.toSet()
    }

}

fun LocalDate.atStartOfWeek(): LocalDate {
    val offset = (dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()
    return minusDays(offset)
}

fun LocalDate.atEndOfWeek(): LocalDate {
    val offset = (DayOfWeek.SUNDAY.value - dayOfWeek.value).toLong()
    return plusDays(offset)
}

fun DayOfWeek.displayName(locale: Locale): String = getDisplayName(TextStyle.FULL, locale)

fun Month.displayName(locale: Locale): String = getDisplayName(TextStyle.FULL, locale)