package ai.zenkai.zenkai.services.tasks.trello

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrelloList(val id: String,
                      val name: String,
                      val closed: Boolean,
                      val idBoard: String,
                      val pos: Int,
                      val subscribed: Boolean) : TrelloEntity()