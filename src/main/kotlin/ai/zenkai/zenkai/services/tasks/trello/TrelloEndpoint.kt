package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.services.BaseEndpoint

const val TRELLO_API = "https://api.trello.com/1"

enum class TrelloEndpoint(url: String) : BaseEndpoint {
    MEMBER("/members/{id}"),
    MEMBER_BOARDS("/members/{id}/boards"),
    BOARDS("/boards"),
    BOARD("/boards/{id}"),
    LISTS("/boards/{id}/lists"),
    POWER_UP("/boards/{id}/boardPlugins"),
    CARDS("/cards"),
    CARD("/cards/{id}");

    override val url = "$TRELLO_API$url"
}