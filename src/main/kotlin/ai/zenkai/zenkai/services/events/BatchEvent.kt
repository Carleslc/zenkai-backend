package ai.zenkai.zenkai.services.events

import ai.zenkai.zenkai.model.Event
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import java.io.IOException
import java.time.ZoneId
import com.google.api.services.calendar.model.Event as ServiceEvent

class BatchEvent(private val timezone: ZoneId) : JsonBatchCallback<ServiceEvent>() {

    val events = mutableListOf<Event>()

    fun <T : Comparable<T>> sorted(maxResults: Int? = null, selector: (Event) -> T): List<Event> {
        return events.sortedBy { selector(it) }.subList(0, minOf(maxResults ?: events.size, events.size))
    }

    override fun onSuccess(serviceEvent: ServiceEvent, responseHeaders: HttpHeaders) {
        events.add(serviceEvent.toEvent(timezone))
    }

    override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders) {
        throw IOException(e.message)
    }

}