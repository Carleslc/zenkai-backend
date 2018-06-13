package ai.zenkai.zenkai.services.calendar

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import java.time.temporal.TemporalUnit

class HumanReadableDuration private constructor(private val start: Temporal, private val end: Temporal, val language: String, val precisionStart: TemporalUnit, val precisionEnd: TemporalUnit, val formatConfiguration: FormatConfiguration) {

    val duration by lazy { Duration.ofSeconds(start.until(end, ChronoUnit.SECONDS)) }

    private val formattedDuration by lazy { format() }

    private fun format(): String {
        var lastSeparatorIndex = -1
        var date: Temporal = start
        var first = true

        fun StringBuilder.addTime(unit: TemporalUnit, td1: String, td: String) {
            if (precisionStart.duration.seconds >= unit.duration.seconds && precisionEnd.duration.seconds <= unit.duration.seconds) {
                val time = date.until(end, unit)
                if (time > 0) {
                    if (!first) {
                        lastSeparatorIndex = length
                        append(formatConfiguration.outerSeparator)
                    }
                    append(time.toString()).append(formatConfiguration.innerSeparator).append(if (time == 1L) td1 else td)
                    date = date.plus(time, unit)
                    first = false
                }
            }
        }

        var formatted = buildString {
            addTime(ChronoUnit.YEARS, formatConfiguration.year, formatConfiguration.years)
            addTime(ChronoUnit.MONTHS, formatConfiguration.month, formatConfiguration.months)
            addTime(ChronoUnit.DAYS, formatConfiguration.day, formatConfiguration.days)
            addTime(ChronoUnit.HOURS, formatConfiguration.hour, formatConfiguration.hours)
            addTime(ChronoUnit.MINUTES, formatConfiguration.minute, formatConfiguration.minutes)
            addTime(ChronoUnit.SECONDS, formatConfiguration.second, formatConfiguration.seconds)
        }

        if (lastSeparatorIndex >= 0 && formatConfiguration.lastOuterSeparator != formatConfiguration.outerSeparator) {
            formatted = formatted.replaceRange(lastSeparatorIndex, lastSeparatorIndex + formatConfiguration.outerSeparator.length, formatConfiguration.lastOuterSeparator)
        }
        return formatted
    }

    override fun toString() = formattedDuration

    companion object Factory {

        fun of(start: Temporal, end: Temporal, language: String, precisionStart: TemporalUnit = ChronoUnit.YEARS, precisionEnd: TemporalUnit = ChronoUnit.MINUTES, formatConfiguration: FormatConfiguration = FormatConfiguration.default(language)): HumanReadableDuration {
            return HumanReadableDuration(start, end, language, precisionStart, precisionEnd, formatConfiguration)
        }

        fun of(duration: TemporalAmount, language: String, precisionStart: TemporalUnit = ChronoUnit.YEARS, precisionEnd: TemporalUnit = ChronoUnit.MINUTES, formatConfiguration: FormatConfiguration = FormatConfiguration.default(language)): HumanReadableDuration {
            val ref = LocalDateTime.now()
            return HumanReadableDuration(ref, ref.plus(duration), language, precisionStart, precisionEnd, formatConfiguration)
        }

    }

    data class FormatConfiguration(val year: String, val years: String,
                                   val month: String, val months: String,
                                   val day: String, val days: String,
                                   val hour: String, val hours: String,
                                   val minute: String, val minutes: String,
                                   val second: String, val seconds: String,
                                   val outerSeparator: String, val innerSeparator: String,
                                   val lastOuterSeparator: String = outerSeparator) {

        constructor(year: String, month: String, day: String, hour: String, minute: String, second: String, outerSeparator: String, innerSeparator: String, lastOuterSeparator: String = outerSeparator)
                : this(year, year, month, month, day, day, hour, hour, minute, minute, second, second, outerSeparator, innerSeparator, lastOuterSeparator)

        companion object Factory {

            fun default(language: String) = FormatConfiguration(
                    i18n[S.YEAR, language], i18n[S.YEARS, language],
                    i18n[S.MONTH, language], i18n[S.MONTHS, language],
                    i18n[S.DAY, language], i18n[S.DAYS, language],
                    i18n[S.HOUR, language], i18n[S.HOURS, language],
                    i18n[S.MINUTE, language], i18n[S.MINUTES, language],
                    i18n[S.SECOND, language], i18n[S.SECONDS, language],
                    ", ", " ", " ${i18n[S.AND, language]} ")

            fun single() = FormatConfiguration("y", "M", "d", "h", "m", "s", " ", "")

        }
    }

}