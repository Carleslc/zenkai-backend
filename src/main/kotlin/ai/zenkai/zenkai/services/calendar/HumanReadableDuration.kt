package ai.zenkai.zenkai.services.calendar

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalUnit

class HumanReadableDuration(val start: Temporal, val end: Temporal, val language: String, val precision: TemporalUnit = ChronoUnit.SECONDS) {

    private val formattedDuration by lazy { format() }

    private fun format(): String {
        var date: Temporal = start
        var first = true

        fun StringBuilder.addTime(unit: TemporalUnit, td1: String, td: String) {
            val time = date.until(end, unit)
            if (time > 0 && precision.duration.seconds <= unit.duration.seconds) {
                if (!first) append(", ")
                append(time.toString()).append(' ').append(if (time == 1L) td1 else td)
                date = date.plus(time, unit)
                first = false
            }
        }

        var formatted = buildString {
            addTime(ChronoUnit.YEARS, i18n[S.YEAR, language], i18n[S.YEARS, language])
            addTime(ChronoUnit.MONTHS, i18n[S.MONTH, language], i18n[S.MONTHS, language])
            addTime(ChronoUnit.DAYS, i18n[S.DAY, language], i18n[S.DAYS, language])
            addTime(ChronoUnit.HOURS, i18n[S.HOUR, language], i18n[S.HOURS, language])
            addTime(ChronoUnit.MINUTES, i18n[S.MINUTE, language], i18n[S.MINUTES, language])
            addTime(ChronoUnit.SECONDS, i18n[S.SECOND, language], i18n[S.SECONDS, language])
        }

        val lastComma = formatted.indexOfLast { it == ',' }
        if (lastComma >= 0) {
            formatted = formatted.replaceRange(lastComma, lastComma + 1, " " + i18n[S.AND, language])
        }
        return formatted
    }

    override fun toString() = formattedDuration

}