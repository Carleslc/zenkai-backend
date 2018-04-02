package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.services.Parameters
import ai.zenkai.zenkai.services.Service
import ai.zenkai.zenkai.services.ServiceEntity
import ai.zenkai.zenkai.services.parameters

typealias TrelloEntity = ServiceEntity<Trello>

interface TrelloService : Service {

    fun getMember(memberId: String, params: Parameters = parameters()): Member

    fun getMe(params: Parameters = parameters()): Member

    fun getBoard(boardId: String, params: Parameters = parameters()): Board

    fun getBoards(memberId: String, params: Parameters = parameters()): List<Board>

    fun getMyBoards(params: Parameters = parameters()): List<Board>

    fun newBoard(name: String, params: Parameters = parameters()): Board

    fun newList(boardId: String, name: String, params: Parameters = parameters()): TrelloList

}