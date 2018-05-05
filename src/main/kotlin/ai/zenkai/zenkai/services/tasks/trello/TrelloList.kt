package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.services.Parameters
import ai.zenkai.zenkai.services.parameters
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.ZonedDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrelloList(val id: String,
                      val name: String?,
                      val closed: Boolean?,
                      val idBoard: String?,
                      val pos: Int?,
                      val subscribed: Boolean?,
                      val cards: List<Card>?) : TrelloEntity() {

    fun newCard(name: String, due: ZonedDateTime? = null, params: Parameters = parameters()) = service.newCard(id, name, due, params)

    fun moveCard(cardId: String) {
        service.moveCard(cardId, id)
    }

    override fun attachService(service: Trello) {
        super.attachService(service)
        cards?.forEach { it.attachService(service) }
    }

}