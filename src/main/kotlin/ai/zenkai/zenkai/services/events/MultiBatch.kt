package ai.zenkai.zenkai.services.events

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.services.calendar.CalendarRequest
import com.google.api.services.calendar.Calendar as GoogleCalendarService

class MultiBatch<T>(private val service: GoogleCalendarService, private val callback: JsonBatchCallback<T>) {

    private val batches = mutableListOf<BatchRequest>()

    private val current: BatchRequest
        get() = batches.last()

    init {
        newBatch()
    }

    fun queue(request: CalendarRequest<T>) {
        checkLimit()
        request.queue(current, callback)
    }

    fun execute() = batches.forEach {
        if (it.size() > 0) {
            it.execute()
        }
    }

    private fun newBatch() = batches.add(service.batch())

    private fun checkLimit() {
        if (current.size() == BATCH_API_REQUEST_LIMIT) {
            newBatch()
        }
    }

    companion object {
        const val BATCH_API_REQUEST_LIMIT = 50
    }

}

fun <T> CalendarRequest<T>.queue(batches: MultiBatch<T>) {
    batches.queue(this)
}

fun <T> GoogleCalendarService.multiBatch(callback: JsonBatchCallback<T>) = MultiBatch(this, callback)