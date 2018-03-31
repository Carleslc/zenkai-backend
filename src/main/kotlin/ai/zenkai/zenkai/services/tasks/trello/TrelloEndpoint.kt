package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.services.BaseEndpoint

const val TRELLO_API = "https://api.trello.com/1"

enum class TrelloEndpoint(url: String) : BaseEndpoint {
    BOARDS("/boards"),
    MY_BOARDS("/members/me/boards");

    override val url = "$TRELLO_API$url"
}