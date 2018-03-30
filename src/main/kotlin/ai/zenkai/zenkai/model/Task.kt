package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.services.calendar.CalendarService
import java.time.LocalDateTime
import java.time.ZoneId

data class Task(val title: String,
                val description: String,
                val status: TaskStatus,
                val deadline: LocalDateTime? = null,
                val tags: List<String>) {

    fun getDisplayText(language: String, zoneId: ZoneId, calendarService: CalendarService) = buildString {
        appendln(title)
        if (deadline != null) {
            append(i18n[S.DEADLINE, language]).append(' ').appendln(calendarService.prettyApproxDateTime(deadline, zoneId, language))
        }
        appendln(description)
        if (tags.isNotEmpty()) {
            appendln(tags.joinToString(prefix = "Tags: "))
        }
    }

    fun getSpeech(language: String, zoneId: ZoneId, calendarService: CalendarService) = buildString {
        append(title)
        if (deadline != null) {
            append(' ').append(i18n[S.DEADLINE_SPEECH, language]).append(' ').append(calendarService.prettyApprox(deadline, zoneId, language))
        }
    }

}