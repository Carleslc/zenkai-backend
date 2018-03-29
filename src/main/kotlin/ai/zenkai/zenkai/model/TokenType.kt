package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.config.TRELLO_API_KEY
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n

enum class TokenType(private val lower: String, private val pretty: String, val authParams: String) {
    TRELLO ("trello", "Trello", "expiration=never&name=${i18n[S.NAME, i18n.default()]}&scope=read,write&response_type=token&key=$TRELLO_API_KEY"),
    TOGGL ("toggl", "Toggl", ""),
    GOOGLE_CALENDAR ("google-calendar", "Google Calendar", "");

    val param by lazy { "$lower-token" }

    override fun toString() = pretty
}