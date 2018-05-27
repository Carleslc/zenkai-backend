package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.model.Event
import ai.zenkai.zenkai.services.Service
import java.time.LocalDate
import java.time.LocalDateTime

interface EventService : Service {

    fun readFollowingEvents(n: Int, maxDate: LocalDateTime? = null): List<Event>

    fun readEvents(date: LocalDate): List<Event>

    fun createEvent(event: Event): Event

    fun createEvent(eventQuery: String): Event

    fun findEvent(title: String): Event?

    fun removeEvent(event: Event)

}