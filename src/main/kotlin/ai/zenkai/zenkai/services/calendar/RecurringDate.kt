package ai.zenkai.zenkai.services.calendar

import org.ocpsoft.prettytime.nlp.parse.DateGroup
import java.time.ZoneId

data class RecurringDate(private val timezone: ZoneId, private val dateGroup: DateGroup) {

    fun isRecurring() = dateGroup.isRecurring

    fun getRecurringMinutes() = dateGroup.recurInterval / (1000 * 60)

    fun getRecurringDays(): Int = (getRecurringMinutes() / (60 * 24)).toInt()

    fun getRecurringUntil() = dateGroup.recursUntil.toInstant().atZone(timezone).toLocalDateTime()

    fun nextDate() = dateGroup.dates.first().toInstant().atZone(timezone).toLocalDateTime()

}