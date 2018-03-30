package ai.zenkai.zenkai.services.calendar

import arrow.data.Try
import me.carleslc.kotlin.extensions.standard.getOrNull
import me.carleslc.kotlin.extensions.time.date
import java.time.LocalDate

data class DatePeriod(val start: LocalDate, val end: LocalDate, @Transient private val calendarService: CalendarService) {

    constructor(date: LocalDate, calendarService: CalendarService) : this(date.atStartOfWeek(), date.atEndOfWeek(), calendarService)

    constructor(start: String, end: String, calendarService: CalendarService)
            : this(calendarService.parse(start), calendarService.parse(end), calendarService)

    val size by lazy { end.toEpochDay() - start.toEpochDay() }

    override fun toString(): String {
        return calendarService.formatDate(start) + '/' + calendarService.formatDate(end)
    }

    companion object Factory {

        fun parse(datePeriod: String, calendarService: CalendarService): DatePeriod? {
            return Try {
                val parts = datePeriod.split('/')
                DatePeriod(parts[0], parts[1], calendarService)
            }.getOrNull()
        }

        fun until(end: LocalDate, calendarService: CalendarService) = DatePeriod(date(), end, calendarService)

        fun default(calendarService: CalendarService) = until(date().plusWeeks(1), calendarService)

    }

}