package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.config.TRELLO_API_KEY
import ai.zenkai.zenkai.i18n.DEFAULT_LANGUAGE
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.toLocale
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.services.parameters
import ai.zenkai.zenkai.services.tasks.trello.*
import org.slf4j.LoggerFactory

class TrelloTaskService(private val accessToken: String,
                        private val tasksListener: TasksListener) : TaskService {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val trello by lazy { Trello(TRELLO_API_KEY, accessToken) }

    private val defaultBoardName by lazy { i18n[S.DEFAULT_BOARD_NAME, language] }

    private val member by lazy { retrieveMe() }

    private val board by lazy { findDefaultBoard() }

    private lateinit var language: String

    /** Sorted tasks (closer deadline first, in other case prevails Trello board list order) **/
    override fun Board.getTasks(status: TaskStatus): List<Task> {
        val locale = language.toLocale()
        val listNames = status.getReadableListNamesLower(locale)
        val selectedLists = board.getLists(parameters(
                "filter" to "open",
                "fields" to "name",
                "cards" to "open",
                "card_fields" to "name,desc,due,labels,shortUrl"
        )).filter { it.name!!.toLowerCase(locale) in listNames }
        logger.info("Selected lists: $selectedLists")
        val tasks = mutableListOf<Task>()
        selectedLists.forEach {
            val listStatus = listNames[it.name!!.toLowerCase(locale)]!!
            tasks.addAll(it.cards!!.map { it.toTask(listStatus) })
        }
        logger.info("Tasks: $tasks")
        return tasks
    }

    fun getDefaultBoard() = board

    fun getMe() = member

    private fun retrieveMe(): Member {
        val me = trello.getMe(parameters(
                "fields" to "username,email,prefs",
                "boards" to "open,member",
                "board_fields" to "name,shortUrl"))
        language = me.getLocale()!!.language
        if (language !in i18n) {
            logger.info("Language $language not supported, default to $DEFAULT_LANGUAGE")
            language = DEFAULT_LANGUAGE
        }
        return me
    }

    private fun findDefaultBoard(): Board {
        val zenkaiBoard = member.boards!!.find { it.name == defaultBoardName }
        logger.info("Zenkai Board: $zenkaiBoard")
        return zenkaiBoard ?: newDefaultBoard()
    }

    private fun newDefaultBoard(): Board {
        logger.info("Creating new board with language $language for ${member.username} with email ${member.email}")
        val trelloLocaleIsSupported = member.getLocale()!!.language in i18n
        val board = trello.newBoard(defaultBoardName, parameters(
                "defaultLists" to "$trelloLocaleIsSupported",
                "powerUps" to "cardAging",
                "prefs_cardAging" to "regular",
                "desc" to i18n[S.DEFAULT_BOARD_DESCRIPTION, language]))
        board.enablePowerUp(PowerUp.CARD_AGING.id)
        if (!trelloLocaleIsSupported) {
            val bottom = parameters("pos" to "bottom")
            board.newList(i18n[S.TODO, DEFAULT_LANGUAGE], bottom)
            board.newList(i18n[S.DOING, DEFAULT_LANGUAGE], bottom)
            board.newList(i18n[S.DONE, DEFAULT_LANGUAGE], bottom)
        }
        tasksListener.onNewBoard(board)
        return board
    }

}

private fun Card.toTask(status: TaskStatus) = Task(name!!, desc!!, status, due, listOf())