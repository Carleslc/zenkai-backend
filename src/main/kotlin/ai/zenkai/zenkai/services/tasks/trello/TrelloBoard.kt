package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.services.Parameters
import ai.zenkai.zenkai.services.parameters
import ai.zenkai.zenkai.services.tasks.Board
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.ZonedDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrelloBoard(val id: String,
                 val name: String?,
                 val desc: String?,
                 val closed: Boolean?,
                 val idOrganization: String?,
                 val pinned: Boolean?,
                 val shortLink: String?,
                 val powerUps: List<String>?,
                 val dateLastActivity: ZonedDateTime?,
                 val idTags: List<String>?,
                 val invited: Boolean?,
                 val starred: Boolean?,
                 val url: String?,
                 val subscribed: Boolean?,
                 val dateLastView: ZonedDateTime?,
                 val shortUrl: String?,
                 var lists: List<TrelloList>?) : Board, TrelloEntity() {

    fun newList(name: String, params: Parameters = parameters()) = service.newList(id, name, params)

    fun getLists(params: Parameters = parameters(), override: Boolean = false): List<TrelloList> {
        if (lists == null || override) {
            lists = service.getLists(id, params)
        }
        return lists!!
    }

    fun enablePowerUp(powerUpId: String) = service.enablePowerUp(id, powerUpId)

    override fun getAccessUrl() = shortUrl

    override fun attachService(service: Trello) {
        super.attachService(service)
        lists?.forEach { it.attachService(service) }
    }

}