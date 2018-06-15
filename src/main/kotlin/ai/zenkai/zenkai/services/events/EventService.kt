package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.model.Event
import ai.zenkai.zenkai.services.Service
import java.time.LocalDate
import java.time.LocalDateTime

interface EventService : Service {

    fun getEvents(start: LocalDateTime? = null, end: LocalDateTime? = null, maxResults: Int? = null): List<Event>

    fun readFollowingEvents(n: Int, maxDate: LocalDateTime? = null): List<Event>

    fun readEvents(date: LocalDate): List<Event>

    fun createEvent(event: Event): Event

    fun createEvent(eventQuery: String): Event

    fun createEvents(events: Collection<Event>): List<Event>

    fun findEvent(query: String): Event?

    fun findEvents(query: String): List<Event>

    fun removeEvent(event: Event)

    fun removeEvents(query: String)

    fun removeEvents(ids: Collection<String>)

}