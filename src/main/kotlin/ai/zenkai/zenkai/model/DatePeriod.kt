package ai.zenkai.zenkai.model

import me.carleslc.kotlin.extensions.time.date
import java.time.LocalDate

data class DatePeriod(val start: LocalDate, val end: LocalDate) {

    companion object Factory {

        fun until(end: LocalDate) = DatePeriod(date(), end)

        fun default() = until(date().plusWeeks(1))

    }

}