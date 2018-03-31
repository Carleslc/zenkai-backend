package ai.zenkai.zenkai.services.tasks.trello

import ai.zenkai.zenkai.services.Parameters
import ai.zenkai.zenkai.services.Service
import ai.zenkai.zenkai.services.ServiceEntity

typealias TrelloEntity = ServiceEntity<Trello>

interface TrelloService : Service {

    fun getBoards(params: Parameters = mutableMapOf()): List<Board>

    fun newBoard(name: String, params: Parameters = mutableMapOf()): Board

}