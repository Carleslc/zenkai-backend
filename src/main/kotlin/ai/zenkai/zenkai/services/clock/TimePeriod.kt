package ai.zenkai.zenkai.services.clock

import arrow.data.Try
import me.carleslc.kotlin.extensions.standard.getOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class TimePeriod(val start: LocalTime, val end: LocalTime) {

    companion object Factory {

        private val timePeriodFormatter by lazy { DateTimeFormatter.ofPattern("HH:mm") }

        fun default() = TimePeriod(LocalTime.MIN, LocalTime.MAX)

        fun parse(s: String): TimePeriod? {
            val parts = s.split('/')
            return Try { TimePeriod(LocalTime.from(timePeriodFormatter.parse(parts[0])), LocalTime.from(timePeriodFormatter.parse(parts[1]))) }.getOrNull()
        }

    }

}