package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.services.tasks.trello.Board

interface TasksListener {

    fun onNewBoard(board: Board)

}