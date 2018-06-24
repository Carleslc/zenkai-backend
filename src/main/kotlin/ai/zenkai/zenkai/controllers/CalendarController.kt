package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.LazyLogger
import ai.zenkai.zenkai.clean
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.services.calendar.CalendarService
import ai.zenkai.zenkai.services.calendar.DatePeriod
import me.carleslc.kotlin.extensions.standard.isNull
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.slf4j.Logger
import org.springframework.stereotype.Controller
import java.time.DayOfWeek

@Controller
class CalendarController(private val calendarService: CalendarService) : BaseController {

    val logger: Logger by LazyLogger()

    override val actionMap: Map<String, Handler> = mapOf(
            "date.get" to { b -> b.calendar() },
            "date.get.period" to { b -> b.calendarPeriod() }
    )

    fun Bot.calendarPeriod() {
        val today = calendarService.today(timezone)
        val dayOfWeek = getString("day-of-week")?.let { DayOfWeek.valueOf(it) }
        val period = getDate("period")?.let { DatePeriod(it, calendarService) }
        val original = getString("period-original")?.toLowerCase(locale).orEmpty()

        val date = calendarService.inPeriod(dayOfWeek ?: today.dayOfWeek,
                period ?: DatePeriod.default(calendarService, timezone), today)

        val format = if (dayOfWeek.isNull()) {
            calendarService.prettyDate(date, language)
        } else {
            calendarService.getDayOfMonth(date, language)
        }

        val originalString = original.clean().capitalize()
        var be = isOrWas(date)
        if (originalString.isBlank()) be = be.capitalize()
        addMessage("$originalString $be $format".trim())
    }

    fun Bot.calendar() {
        val today = calendarService.today(timezone)
        var date = getDate("date") ?: today
        val original = getString("date-original")?.toLowerCase(locale) ?: get(S.TODAY)
        val dateAsk = getString("date-ask")?.clean()?.toLowerCase(locale)
        val isAsking = dateAsk.isNotNullOrBlank()

        fun processAskingOr(notAsking: () -> String): String {
            return if (isAsking) {
                if (calendarService.isDayOfWeek(dateAsk!!, language)) {
                    logger.info("Asking Day of Week")
                    calendarService.getDayOfWeek(date, language)
                } else {
                    logger.info("Asking Day of Month")
                    calendarService.getDayOfMonth(date, language)
                }
            } else notAsking()
        }

        val queryDayOfWeek = calendarService.isDayOfWeek(original, language)

        if (calendarService.isPast(query, language) && date >= today) {
            date = if (queryDayOfWeek) date.minusWeeks(1) else date.minusYears(1)
        }

        val format = when {
            queryDayOfWeek -> {
                logger.info("Query Day of Week")
                processAskingOr { calendarService.getDayOfMonth(date, language) }
            }
            calendarService.isDayOfMonth(original, language) -> {
                logger.info("Query Day of Month")
                processAskingOr { calendarService.getDayOfWeek(date, language) }
            }
            else -> processAskingOr { calendarService.prettyDate(date, language) }
        }

        addMessage("${original.clean().capitalize()} ${isOrWas(date)} $format")
    }

}