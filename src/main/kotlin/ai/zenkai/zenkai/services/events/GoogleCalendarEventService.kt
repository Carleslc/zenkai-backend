package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.model.Event
import ai.zenkai.zenkai.services.calendar.shiftToday
import ai.zenkai.zenkai.services.calendar.withOffset
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.*
import com.google.api.services.calendar.model.Calendar
import me.carleslc.kotlin.extensions.collections.L
import me.carleslc.kotlin.extensions.standard.alsoIf
import me.carleslc.kotlin.extensions.standard.isNotNull
import me.carleslc.kotlin.extensions.time.toDate
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.time.*
import java.util.*
import com.google.api.services.calendar.Calendar as GoogleCalendarService
import com.google.api.services.calendar.model.Event as ServiceEvent

class GoogleCalendarEventService(private val service: GoogleCalendarService,
                                 private val timezone: ZoneId,
                                 private val language: String,
                                 private val calendarListener: CalendarListener? = null) : EventService {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val calendars by lazy { retrieveCalendars() }

    private lateinit var defaultCalendarId: String

    fun configure(): GoogleCalendarEventService {
        defaultCalendarId = URLDecoder.decode(findDefaultCalendar().id, "UTF-8")
        return this
    }

    override fun readFollowingEvents(n: Int, maxDate: LocalDateTime?): List<Event> {
        return getEvents(maxResults=n, end=maxDate)
    }

    override fun readEvents(date: LocalDate): List<Event> {
        return getEvents(start=date.atStartOfDay(), end=date.plusDays(1).atStartOfDay())
    }

    override fun createEvent(event: Event): Event {
        return createServiceEvent(event).execute().toEvent(timezone)
    }

    override fun createEvent(eventQuery: String): Event {
        return service.events().quickAdd(defaultCalendarId, eventQuery).setFields(EVENT_FIELDS).execute().toEvent(timezone)
    }

    override fun createEvents(events: Collection<Event>): List<Event> {
        val merge = BatchEvent(timezone)
        val batch = service.multiBatch(merge)
        events.forEach {
            createServiceEvent(it).queue(batch)
        }
        batch.execute()
        return merge.sorted { it.start }
    }

    override fun findEvent(query: String) = findServiceEvents(query).firstOrNull()?.toEvent(timezone)

    override fun findEvents(query: String) = findServiceEvents(query).map { it.toEvent(timezone) }

    override fun removeEvent(event: Event) {
        event.id?.let {
            service.events().delete(defaultCalendarId, it).execute()
        }
    }

    override fun removeEvents(ids: Collection<String>) {
        val batch = service.multiBatch(BatchEventsVoid())
        ids.forEach {
            service.events().delete(defaultCalendarId, it).queue(batch)
        }
        batch.execute()
    }

    override fun removeEvents(query: String) = removeEvents(findServiceEvents(query, false).map { it.id })

    override fun getEvents(start: LocalDateTime?, end: LocalDateTime?, maxResults: Int?): List<Event> {
        val merge = BatchEvents(timezone)
        val batch = service.multiBatch(merge)
        val now = ZonedDateTime.now(timezone)
        calendars.forEach {
            service.events().list(it.id)
                .setTimeMin(start.shiftToday(now).toDateTime())
                .setTimeZone(timezone.id)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .alsoIf({ end.isNotNull() }) { it.timeMax = end!!.toDateTime(timezone) }
                .alsoIf({ maxResults.isNotNull() }) { it.maxResults = maxResults }
                .setFields("items($EVENT_FIELDS)")
                .queue(batch)
        }
        batch.execute()
        return merge.sorted(maxResults) { it.start }
    }

    private fun createServiceEvent(event: Event) = service.events().insert(defaultCalendarId, event.toServiceEvent()).setFields(EVENT_FIELDS)

    private fun findServiceEvents(query: String, sorted: Boolean = true): List<ServiceEvent> {
        val events = service.events().list(defaultCalendarId)
                .setQ(query)
                .setTimeMin(ZonedDateTime.now(timezone).toDateTime())
                .setTimeZone(timezone.id)
                .setFields("items($EVENT_FIELDS)")
                .execute().items
        return if (sorted) events.sortedBy { it.start.dateTime.value } else events
    }

    private fun findDefaultCalendar(): CalendarListEntry {
        return calendars.find { it.isDefaultCalendar() } ?: newDefaultCalendar()
    }

    private fun retrieveCalendars(): MutableList<CalendarListEntry> {
        val calendars = mutableListOf<CalendarListEntry>()
        var pageToken: String? = null
        do {
            val calendarList = service.calendarList().list().setPageToken(pageToken).setFields("items($CALENDAR_LIST_FIELDS)").execute()
            calendars.addAll(calendarList.items.filter { it.isSelected || it.isDefaultCalendar() })
            pageToken = calendarList.nextPageToken
        } while (pageToken != null)
        return calendars
    }

    private fun newDefaultCalendar(): CalendarListEntry {
        logger.info("Creating new default calendar")
        calendarListener?.onNewCalendar()
        val calendar = service.calendars().insert(Calendar().apply { // time consuming (~5s)
            summary = DEFAULT_CALENDAR_NAME
            description = i18n[S.DEFAULT_CALENDAR_DESCRIPTION, language]
            timeZone = timezone.id
        }).setFields(CALENDAR_FIELDS).execute()
        logger.info("Updating list entry")
        val calendarListEntry = service.calendarList().patch(calendar.id, CalendarListEntry().apply {
            selected = true
            colorId = DEFAULT_COLOR
            defaultReminders = DEFAULT_REMINDER
            notificationSettings = AGENDA
        }).setFields(CALENDAR_LIST_FIELDS).execute()
        logger.info("Calendar created")
        calendars.add(calendarListEntry)
        return calendarListEntry
    }

    companion object {
        const val DEFAULT_COLOR = "15"
        const val CALENDAR_FIELDS = "id"
        const val CALENDAR_LIST_FIELDS = "id,summary,selected"
        const val EVENT_FIELDS = "id,htmlLink,summary,description,location,start/dateTime,end/dateTime"
        val DEFAULT_CALENDAR_NAME = i18n.getNonTranslatable(S.DEFAULT_CALENDAR_NAME)
        private val DEFAULT_REMINDER get() = L[EventReminder().setMethod("popup").setMinutes(10)]
        private val AGENDA get() = CalendarListEntry.NotificationSettings().setNotifications(L[
                CalendarNotification().setMethod("email").setType("agenda")])!!
    }

}

private fun CalendarListEntry.isDefaultCalendar() = summary.equals(GoogleCalendarEventService.DEFAULT_CALENDAR_NAME, true)

private fun LocalDateTime.toDateTime(zoneId: ZoneId) = DateTime(toDate(zoneId), TimeZone.getTimeZone(zoneId))

private fun ZonedDateTime.toDateTime() = toLocalDateTime().toDateTime(zone)

private fun DateTime.toZonedDateTime() = ZonedDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.ofTotalSeconds(timeZoneShift/1000))

internal fun ServiceEvent.toEvent(timezone: ZoneId) = Event(summary.orEmpty(), start.dateTime.toZonedDateTime().withOffset(timezone), end.dateTime.toZonedDateTime().withOffset(timezone), description.orEmpty(), location, htmlLink, id)

internal fun List<ServiceEvent>.toEvents(timezone: ZoneId): List<Event> = map { it.toEvent(timezone) }

private fun Event.toServiceEvent(): ServiceEvent {
    val event = ServiceEvent()
            .setSummary(title)
            .setDescription(description)
            .setLocation(location)
            .setReminders(ServiceEvent.Reminders().setUseDefault(true))

    event.start = EventDateTime()
            .setDateTime(start.toDateTime())
            .setTimeZone(start.zone.id)

    event.end = EventDateTime()
            .setDateTime(end.toDateTime())
            .setTimeZone(end.zone.id)

    return event
}