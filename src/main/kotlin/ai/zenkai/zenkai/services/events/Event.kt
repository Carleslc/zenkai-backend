package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.HumanReadableDuration
import ai.zenkai.zenkai.services.clock.isSingleHour
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class Event(val title: String,
                 val start: ZonedDateTime,
                 val end: ZonedDateTime,
                 val description: String = "",
                 val location: String? = null,
                 val url: String? = null,
                 val id: String? = null) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getDisplayText(language: String, calendarService: CalendarService) = buildString {
        appendln(title)
        with (calendarService) {
            if (isHappeningNow()) {
                append('(').append(i18n[S.NOW, language].capitalize()).append(") ")
            }
            if (start.toLocalDate() == end.toLocalDate()) {
                append(prettyDate(start.toLocalDate(), language).capitalize())
                append(", ")
                append(clockService.pretty24(start.toLocalTime(), language))
                append(" - ")
                appendln(clockService.pretty24(end.toLocalTime(), language))
            } else {
                append(prettyDateTime(start.toLocalDateTime(), language).capitalize())
                append(" - ")
                appendln(prettyDateTime(end.toLocalDateTime(), language).capitalize())
            }
        }
        append(i18n[S.DURATION, language]).append(": ").appendln(HumanReadableDuration(start, end, language, precision=ChronoUnit.MINUTES))
        if (location.isNotNullOrBlank()) {
            appendln(location)
        }
        appendln()
        if (description.isNotBlank()) {
            appendln(description).appendln()
        }
        url?.let { appendln(url) }
    }

    fun getSpeech(language: String, calendarService: CalendarService) = buildString {
        append(title).append(' ')
        if (isHappeningNow()) {
            if (end.toLocalDate() == calendarService.today(end.zone)) {
                append(i18n[if (end.toLocalTime().isSingleHour()) S.UNTIL_SINGLE else S.UNTIL, language])
                append(' ').append(calendarService.clockService.pretty12(end.toLocalTime(), language))
            } else {
                append(i18n[S.NOW, language])
            }
        } else {
            append(calendarService.prettyApprox(start, language))
        }
        if (location.isNotNullOrBlank()) {
            append(' ').append(i18n[S.AT, language]).append(' ').append(location)
        }
    }

    fun isHappeningNow() = ZonedDateTime.now(start.zone) >= start && ZonedDateTime.now(end.zone) < end

}