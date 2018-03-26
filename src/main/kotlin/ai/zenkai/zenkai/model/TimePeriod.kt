package ai.zenkai.zenkai.model

import java.time.LocalTime

data class TimePeriod(val start: LocalTime, val end: LocalTime) {

    companion object Factory {

        fun default() = TimePeriod(LocalTime.MIN, LocalTime.MAX)

    }

}