package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.add
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.services.Parameters
import ai.zenkai.zenkai.services.RestTemplateHttpClient
import ai.zenkai.zenkai.services.tasks.trello.TrelloEndpoint.BOARDS
import ai.zenkai.zenkai.services.tasks.trello.TrelloEndpoint.MY_BOARDS
import me.carleslc.kotlin.extensions.standard.println

open class Trello(private val applicationKey: String, private val userToken: String) : TrelloService, RestTemplateHttpClient() {

    private val keyToken by lazy { mapOf("key" to applicationKey, "token" to userToken) }

    final override fun getBoards(params: Parameters): List<Board> {
        return attachService(getList(getUrl(MY_BOARDS, params), Board::class, params.withKeyToken()))
    }

    final override fun newBoard(name: String, params: Parameters): Board {
        val richParams = params.add("name" to name)
        return attachService(post(getUrl(BOARDS, richParams), Board::class, richParams.withKeyToken()))
    }

    protected fun <E: TrelloEntity> attachService(entity: E): E = entity.apply { service = this@Trello }

    protected fun attachService(entities: List<Board>): List<Board> {
        entities.forEach { attachService(it) }
        return entities
    }

    private fun Parameters.withKeyToken(): Parameters = add(keyToken)

    private fun getUrl(endpoint: TrelloEndpoint, params: Parameters): String {
        return endpoint.withParameters(keyToken.keys.toMutableList().apply { addAll(params.keys) })
    }

}