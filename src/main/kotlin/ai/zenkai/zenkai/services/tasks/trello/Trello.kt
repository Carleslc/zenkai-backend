package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.add
import ai.zenkai.zenkai.services.Parameters
import ai.zenkai.zenkai.services.RestTemplateHttpClient
import ai.zenkai.zenkai.services.parameters
import ai.zenkai.zenkai.services.tasks.trello.TrelloEndpoint.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

open class Trello(private val applicationKey: String, private val userToken: String) : TrelloService, RestTemplateHttpClient() {

    private val keyToken by lazy { parameters("key" to applicationKey, "token" to userToken) }

    final override fun getMember(memberId: String, params: Parameters): Member {
        return attachService(get(getUrl(MEMBER, params), Member::class, params.withId(memberId).withKeyToken()))
    }

    final override fun getMe(params: Parameters): Member = getMember("me", params)

    final override fun getBoards(memberId: String, params: Parameters): List<Board> {
        return attachService(getList(getUrl(MEMBER_BOARDS, params), Board::class, params.withId(memberId).withKeyToken()))
    }

    final override fun getMyBoards(params: Parameters): List<Board> = getBoards("me", params)

    final override fun getBoard(boardId: String, params: Parameters): Board {
        return attachService(get(getUrl(BOARD, params), Board::class, params.withId(boardId).withKeyToken()))
    }

    final override fun newBoard(name: String, params: Parameters): Board {
        val richParams = params.add("name" to name)
        return attachService(post(getUrl(BOARDS, richParams), Board::class, richParams.withKeyToken()))
    }

    final override fun newList(boardId: String, name: String, params: Parameters): TrelloList {
        val richParams = params.add("name" to name)
        return attachService(post(getUrl(LISTS, richParams), TrelloList::class, richParams.withId(boardId).withKeyToken()))
    }

    final override fun getLists(boardId: String, params: Parameters): List<TrelloList> {
        return attachService(getList(getUrl(LISTS, params), TrelloList::class, params.withId(boardId).withKeyToken()))
    }

    final override fun enablePowerUp(boardId: String, powerUpId: String, params: Parameters): Boolean {
        val richParams = params.add("idPlugin" to powerUpId)
        return post(getUrl(POWER_UP, richParams), richParams.withId(boardId).withKeyToken()).isOk()
    }

    final override fun newCard(listId: String, name: String, due: ZonedDateTime?, params: Parameters): Card {
        val richParams = params.add("idList" to listId, "name" to name, "due" to format(due))
        return post(getUrl(CARDS, richParams), Card::class, richParams.withKeyToken())
    }

    final override fun moveCard(cardId: String, listId: String, params: Parameters) {
        val richParams = params.add("idList" to listId)
        return put(getUrl(CARD, richParams), richParams.withId(cardId).withKeyToken())
    }

    final override fun archiveCard(cardId: String) {
        val richParams = parameters("closed" to "true")
        return put(getUrl(CARD, richParams), richParams.withId(cardId).withKeyToken())
    }

    private fun format(dateTime: ZonedDateTime?) = dateTime?.let { DateTimeFormatter.ISO_INSTANT.format(it) }.orEmpty()

    protected fun <E: TrelloEntity> attachService(entity: E): E = entity.apply { attachService(this@Trello) }

    protected fun <E: TrelloEntity> attachService(entities: List<E>): List<E> {
        entities.forEach { attachService(it) }
        return entities
    }

    protected fun Parameters.withKeyToken(): Parameters = add(keyToken)

    protected fun Parameters.withId(id: String): Parameters = add("id" to id)

    protected fun getUrl(endpoint: TrelloEndpoint, params: Parameters): String {
        return endpoint.withParameters(keyToken.keys.toMutableList().apply { addAll(params.keys) })
    }

}