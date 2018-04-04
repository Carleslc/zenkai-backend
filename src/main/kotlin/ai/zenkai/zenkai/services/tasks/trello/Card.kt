package ai.zenkai.zenkai.services.tasks.trello

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class Card(val id: String,
                val name: String?,
                val desc: String?,
                val closed: Boolean?,
                val due: LocalDateTime?,
                val dueComplete: Boolean?,
                val dateLastActivity: LocalDateTime?,
                val email: String?,
                val idBoard: String?,
                val idList: String?,
                val idShort: Int?,
                val idLabels: List<String>?,
                val shortLink: String?,
                val shortUrl: String?,
                val subscribed: Boolean?,
                val url: String?)  : TrelloEntity()