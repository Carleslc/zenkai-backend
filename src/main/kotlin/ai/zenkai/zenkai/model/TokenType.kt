package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.config.TRELLO_API_KEY
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n

enum class TokenType(private val lower: String, private val pretty: String, val authUrl: String) {
    TRELLO ("trello", "Trello", "https://trello.com/1/authorize?expiration=never&name=${i18n.getNonTranslatable(S.NAME)}&scope=read,write&response_type=token&key=$TRELLO_API_KEY"),
    TOGGL ("toggl", "Toggl", ""),
    GOOGLE_CALENDAR ("google-calendar", "Google Calendar", "");

    val param by lazy { "$lower-token" }

    override fun toString() = pretty
}