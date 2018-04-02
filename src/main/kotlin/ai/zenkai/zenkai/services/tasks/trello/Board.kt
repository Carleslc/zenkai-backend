package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.services.Parameters
import ai.zenkai.zenkai.services.parameters
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class Board(val id: String,
                 val name: String,
                 val desc: String?,
                 val closed: Boolean,
                 val idOrganization: String?,
                 val pinned: Boolean?,
                 val shortLink: String?,
                 val powerUps: List<String>?,
                 val dateLastActivity: LocalDateTime?,
                 val idTags: List<String>?,
                 val invited: Boolean?,
                 val starred: Boolean?,
                 val url: String?,
                 val subscribed: Boolean?,
                 val dateLastView: LocalDateTime?,
                 val shortUrl: String?,
                 val lists: List<TrelloList>?) : TrelloEntity() {

    fun newList(name: String, params: Parameters = parameters()) = service.newList(id, name, params)

    fun enablePowerUp(powerUpId: String) = service.enablePowerUp(id, powerUpId)

    override fun attachService(service: Trello) {
        super.attachService(service)
        lists?.forEach { it.attachService(service) }
    }

}