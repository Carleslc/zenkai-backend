package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.config.TRELLO_API_KEY
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.services.tasks.trello.Board
import ai.zenkai.zenkai.services.tasks.trello.Trello

class TrelloTaskService(private val accessToken: String,
                        private val language: String,
                        private val tasksListener: TasksListener) : TaskService {

    private val trello by lazy { Trello(TRELLO_API_KEY, accessToken) }

    private val defaultBoardName by lazy { i18n[S.DEFAULT_BOARD_NAME, language] }

    private val board by lazy { findDefaultBoard() }

    /** Sorted tasks (closer deadline first, in other case prevails Trello board list order) **/
    override fun Board.getTasks(status: TaskStatus): List<Task> {
        return listOf()
    }

    fun getDefaultBoard() = board

    private fun findDefaultBoard(): Board {
        val zenkaiBoard = trello.getBoards(mutableMapOf("filter" to "open"))
                .find { it.name == defaultBoardName }
        Bot.logger.info("Zenkai Board: $zenkaiBoard")
        return zenkaiBoard ?: newDefaultBoard()
    }

    private fun newDefaultBoard(): Board {
        val board = trello.newBoard(defaultBoardName, mutableMapOf(
                "defaultLists" to "true",
                "powerUps" to "cardAging",
                "desc" to i18n[S.DEFAULT_BOARD_DESCRIPTION, language]))
        tasksListener.onNewBoard(board)
        return board
    }

}