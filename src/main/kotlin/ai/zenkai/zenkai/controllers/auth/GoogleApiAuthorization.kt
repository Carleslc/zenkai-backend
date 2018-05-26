package ai.zenkai.zenkai.controllers.auth

import ai.zenkai.zenkai.config.SERVER
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import arrow.data.Try
import arrow.data.getOrDefault
import com.google.api.client.extensions.appengine.datastore.AppEngineDataStoreFactory
import com.google.api.client.extensions.appengine.http.UrlFetchTransport
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.gson.Gson
import java.io.InputStreamReader
import java.time.ZoneId
import java.util.*

class GoogleApiAuthorization(private val userId: String) {

    private val flow by lazy { newFlow() }

    private val credential by lazy { flow.loadCredential(userId) }

    fun hasValidCredentials(): Boolean {
        if (credential?.accessToken == null) {
            return false
        }
        if (isExpired()) {
            return Try { credential.refreshToken() }.getOrDefault { false }
        }
        return true
    }

    private fun isExpired() = credential.expiresInSeconds <= 0

    fun clear() {
        flow.credentialDataStore.delete(userId)
    }

    fun getCalendar(): Calendar? {
        return if (hasValidCredentials()) {
            Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(i18n.getNonTranslatable(S.NAME)).build()
        } else null
    }

    fun getSimpleAuthorizationUrl(gson: Gson, language: String, timezone: ZoneId): String {
        val url = GenericUrl(SERVER).apply { rawPath = GoogleApiAuthorizationController.URL }.build()
        return "$url?u=${UserConfiguration(userId, language, timezone.id).encode(gson)}"
    }

    fun getAuthorizationUrl(currentState: String) = flow.newAuthorizationUrl().apply {
        redirectUri = buildRedirectUri()
        state = currentState
    }.build()!!

    private fun buildRedirectUri() = GenericUrl(SERVER).apply { rawPath = GoogleApiAuthorizationController.URL }.build()!!

    fun setCredentials(code: String) {
        val response = flow.newTokenRequest(code).setRedirectUri(buildRedirectUri()).execute()
        flow.createAndStoreCredential(response, userId)
    }

    companion object {
        private val DATA_STORE_FACTORY = AppEngineDataStoreFactory.getDefaultInstance()

        val HTTP_TRANSPORT: HttpTransport = UrlFetchTransport()

        private val JSON_FACTORY = JacksonFactory.getDefaultInstance()

        private val clientCredential: GoogleClientSecrets by lazy {
            val inRes = GoogleApiAuthorization::class.java.getResourceAsStream("/client_secret.json")
            GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inRes))
        }

        private fun newFlow() = GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientCredential,
                Collections.singleton(CalendarScopes.CALENDAR))
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build()!!
    }
}