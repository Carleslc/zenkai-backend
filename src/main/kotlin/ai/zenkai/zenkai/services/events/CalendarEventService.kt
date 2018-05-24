package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Calendar
import com.google.api.services.calendar.model.EventDateTime
import me.carleslc.kotlin.extensions.standard.alsoIf
import me.carleslc.kotlin.extensions.standard.isNotNull
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import me.carleslc.kotlin.extensions.time.toDate
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.time.*
import java.util.*
import com.google.api.services.calendar.Calendar as CalendarService
import com.google.api.services.calendar.model.Event as ServiceEvent

class CalendarEventService(private val service: CalendarService,
                           private val timezone: ZoneId,
                           private val language: String,
                           private val calendarListener: CalendarListener) : EventService {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val defaultCalendarName by lazy { i18n.getNonTranslatable(S.DEFAULT_CALENDAR_NAME) }

    private val defaultCalendarId by lazy { URLDecoder.decode(findDefaultCalendar(), "UTF-8") }

    override fun readFollowingEvents(n: Int): List<Event> {
        val now = DateTime(System.currentTimeMillis())
        return configureEvents(start=now, maxResults=n).execute().items.toEvents()
    }

    override fun readEvents(date: LocalDate): List<Event> {
        val start = date.atStartOfDay().toDateTime()
        val end = date.plusDays(1).atStartOfDay().toDateTime()
        return configureEvents(start, end).execute().items.toEvents()
    }

    private fun configureEvents(start: DateTime, end: DateTime? = null, maxResults: Int = 0): CalendarService.Events.List {
        return service.events().list(defaultCalendarId)
                .setTimeMin(start)
                .setTimeZone(timezone.id)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .alsoIf({ end.isNotNull() }) { it.timeMax = end }
                .alsoIf({ maxResults > 0 }) { it.maxResults = maxResults }
    }

    private fun findDefaultCalendar(): String {
        var pageToken: String? = null
        do {
            val calendarList = service.calendarList().list().setPageToken(pageToken).execute()
            val defaultCalendar = calendarList.items.find { it.summary.equals(defaultCalendarName, true) }
            if (defaultCalendar != null) {
                return defaultCalendar.id
            }
            pageToken = calendarList.nextPageToken
        } while (pageToken != null)
        return newDefaultCalendar()
    }

    private fun newDefaultCalendar(): String {
        var calendar = Calendar().apply {
            summary = defaultCalendarName
            description = i18n[S.DEFAULT_CALENDAR_DESCRIPTION, language]
            timeZone = timezone.id
        }
        calendar = service.calendars().insert(calendar).execute()
        calendarListener.onNewCalendar()
        return calendar.id
    }

    private fun LocalDateTime.toDateTime(zoneId: ZoneId = timezone) = DateTime(toDate(), TimeZone.getTimeZone(zoneId))

    private fun ZonedDateTime.toDateTime() = toLocalDateTime().toDateTime(zone)

    private fun DateTime.toZonedDateTime() = ZonedDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.ofTotalSeconds(timeZoneShift/1000))

    private fun ServiceEvent.toEvent() = Event(summary.orEmpty(), start.dateTime.toZonedDateTime(), end.dateTime.toZonedDateTime(), description.orEmpty(), location, htmlLink, id)

    private fun List<ServiceEvent>.toEvents(): List<Event> = map { it.toEvent() }

    private fun Event.toServiceEvent(): ServiceEvent {
        val event = ServiceEvent()
                .setSummary(title)
                .setDescription(description)
                .setLocation(location)

        event.start = EventDateTime()
                .setDateTime(start.toDateTime())
                .setTimeZone(start.zone.id)

        event.end = EventDateTime()
                .setDateTime(end.toDateTime())
                .setTimeZone(end.zone.id)

        return event
    }

}