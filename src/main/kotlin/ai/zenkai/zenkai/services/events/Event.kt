package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.HumanReadableDuration
import ai.zenkai.zenkai.services.calendar.withOffset
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

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
        append(i18n[S.DURATION, language]).append(": ").appendln(HumanReadableDuration(start, end, language))
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
        append(title).append(' ').append(calendarService.prettyApprox(start.withOffset(), language))
        if (location.isNotNullOrBlank()) {
            append(' ').append(i18n[S.AT, language]).append(' ').append(location)
        }
    }

}