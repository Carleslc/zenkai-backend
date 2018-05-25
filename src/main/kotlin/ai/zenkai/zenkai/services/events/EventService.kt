package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.services.Service
import java.time.LocalDate

interface EventService : Service {

    fun readFollowingEvents(n: Int, calendarId: String? = null): List<Event>

    fun readEvents(date: LocalDate, calendarId: String? = null): List<Event>

    fun createEvent(event: Event, calendarId: String? = null): Event

    fun createEvent(eventQuery: String, calendarId: String? = null): Event

}