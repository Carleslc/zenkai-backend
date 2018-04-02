package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.config.TRELLO_API_KEY
import ai.zenkai.zenkai.i18n.DEFAULT_LANGUAGE
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.toLocale
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.services.Parameters
import ai.zenkai.zenkai.services.parameters
import ai.zenkai.zenkai.services.tasks.trello.Board
import ai.zenkai.zenkai.services.tasks.trello.Member
import ai.zenkai.zenkai.services.tasks.trello.Trello
import java.util.*

class TrelloTaskService(private val accessToken: String,
                        private val language: String,
                        private val tasksListener: TasksListener) : TaskService {

    private val trello by lazy { Trello(TRELLO_API_KEY, accessToken) }

    private val defaultBoardName by lazy { i18n[S.DEFAULT_BOARD_NAME, language] }

    private val member by lazy { retrieveMe() }

    private val board by lazy { findDefaultBoard() }

    /** Sorted tasks (closer deadline first, in other case prevails Trello board list order) **/
    override fun Board.getTasks(status: TaskStatus): List<Task> {
        return listOf()
    }

    fun getDefaultBoard() = board

    fun getMe() = member

    private fun retrieveMe(): Member {
        return trello.getMe(parameters(
                "boards" to "open,member",
                "board_lists" to "open",
                "board_fields" to "name,shortUrl"))
    }

    private fun findDefaultBoard(): Board {
        val zenkaiBoard = member.boards!!.find { it.name == defaultBoardName }
        Bot.logger.info("Zenkai Board: $zenkaiBoard")
        return zenkaiBoard ?: newDefaultBoard()
    }

    private fun newDefaultBoard(): Board {
        val language = member.getLanguage()
        Bot.logger.info("Creating new board with language $language for ${member.username} with email ${member.email}")
        val trelloLocaleIsSupported = language in i18n
        val board = trello.newBoard(defaultBoardName, parameters(
                "defaultLists" to "$trelloLocaleIsSupported",
                "powerUps" to "cardAging",
                "desc" to i18n[S.DEFAULT_BOARD_DESCRIPTION, language]))
        if (!trelloLocaleIsSupported) {
            Bot.logger.info("Language $language not supported, default to $DEFAULT_LANGUAGE")
            val bottom = parameters("pos" to "bottom")
            board.newList(i18n[S.TODO, DEFAULT_LANGUAGE], bottom)
            board.newList(i18n[S.DOING, DEFAULT_LANGUAGE], bottom)
            board.newList(i18n[S.DONE, DEFAULT_LANGUAGE], bottom)
        }
        tasksListener.onNewBoard(board)
        return board
    }

    private fun Member.getLanguage() = getLocale().language

}