package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.model.Event
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.calendar.model.Events
import java.io.IOException
import java.time.ZoneId

class BatchEvents(private val timezone: ZoneId) : JsonBatchCallback<Events>() {

    val events = mutableListOf<Event>()

    fun <T : Comparable<T>> sorted(maxResults: Int? = null, selector: (Event) -> T): List<Event> {
        return events.sortedBy { selector(it) }.subList(0, minOf(maxResults ?: events.size, events.size))
    }

    override fun onSuccess(serviceEvents: Events, responseHeaders: HttpHeaders) {
        events.addAll(serviceEvents.items.toEvents(timezone))
    }

    override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders) {
        throw IOException(e.message)
    }

}