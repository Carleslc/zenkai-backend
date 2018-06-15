package ai.zenkai.zenkai.services.events

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import java.io.IOException

class BatchEventsVoid : JsonBatchCallback<Void>() {

    override fun onSuccess(any: Void?, responseHeaders: HttpHeaders) {
        // empty
    }

    override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders) {
        throw IOException(e.message)
    }

}