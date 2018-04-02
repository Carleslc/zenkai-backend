package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.config.TRELLO_API_KEY
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n

enum class TokenType(val lower: String, val pretty: String,
                     val regex: String, val event: String, val authUrl: String) {
    TRELLO ("trello", "Trello", "[a-z0-9]{64}", "tasks-read","https://trello.com/1/authorize?expiration=never&name=${i18n.getNonTranslatable(S.NAME)}&scope=read,write,account&response_type=token&key=$TRELLO_API_KEY"),
    TOGGL ("toggl", "Toggl", "", "", ""),
    GOOGLE_CALENDAR ("google-calendar", "Google Calendar", "", "", "");

    val param by lazy { "$lower-token" }

    override fun toString() = pretty
}