package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.cleanFormat
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.HumanReadableDuration
import me.carleslc.kotlin.extensions.standard.letIf
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

data class Task(val title: String,
                val description: String,
                val status: TaskStatus,
                val duration: Duration,
                val deadline: LocalDateTime? = null,
                val url: String? = null,
                val tags: List<String> = listOf(),
                val id: String = "") {

    fun hasSimilarTitle(title: String, locale: Locale): Boolean {
        val t1 = this.title.cleanFormat(locale)
        return t1.contains(title) || title.contains(t1)
    }

    fun isSimilar(other: Task, locale: Locale) = hasSimilarTitle(other.title.cleanFormat(locale), locale)

    fun getDisplayText(language: String, zoneId: ZoneId, calendarService: CalendarService) = buildString {
        appendln(title)
        HumanReadableDuration.of(duration, language).toString().letIf(String::isNotBlank) {
            append(i18n[S.DURATION, language]).append(": ").appendln(it)
        }
        deadline?.let {
            append(i18n[S.DEADLINE, language]).append(' ')
                    .appendln(calendarService.prettyApproxDateTime(deadline, zoneId, language))
        }
        if (description.isNotBlank()) {
            appendln().appendln(description).appendln()
        }
        if (tags.isNotEmpty()) {
            appendln(tags.joinToString(prefix = "Tags: ")).appendln()
        }
        url?.let { appendln(it) }
    }

    fun getSpeech(language: String, zoneId: ZoneId, calendarService: CalendarService) = buildString {
        append(title)
        if (deadline != null) {
            append(' ').append(i18n[S.DEADLINE_SPEECH, language])
            append(' ').append(calendarService.prettyApprox(deadline, zoneId, language))
        }
    }

    companion object {

        fun priorityComparator() = Comparator<Task> { t1, t2 ->
            if (t1.deadline != null) {
                if (t2.deadline != null) t2.deadline.compareTo(t1.deadline) else 1
            } else {
                if (t2.deadline != null) -1 else t2.status.compareTo(t1.status)
            }
        }.reversed()!!

        fun statusComparator(): Comparator<Task> {
            val priority = priorityComparator()
            return Comparator<Task> { t1, t2 ->
                val statusComparison = t2.status.compareTo(t1.status)
                if (statusComparison != 0) statusComparison else priority.compare(t1, t2)
            }
        }

    }

}