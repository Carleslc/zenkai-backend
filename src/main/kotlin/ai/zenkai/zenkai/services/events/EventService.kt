package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.services.Service
import java.time.LocalDate

interface EventService : Service {

    fun readFollowingEvents(n: Int): List<Event>

    fun readEvents(date: LocalDate): List<Event>

}