package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.add
import ai.zenkai.zenkai.config.TRELLO_API_KEY
import ai.zenkai.zenkai.i18n.DEFAULT_LANGUAGE
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.toLocale
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.services.parameters
import ai.zenkai.zenkai.services.tasks.trello.*
import me.carleslc.kotlin.extensions.standard.letOrElse
import org.slf4j.LoggerFactory
import java.time.ZoneId

class TrelloTaskService(private val accessToken: String,
                        private val tasksListener: TasksListener,
                        private val zoneId: ZoneId) : TaskService {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val trello by lazy { Trello(TRELLO_API_KEY, accessToken) }

    private val defaultBoardName by lazy { i18n[S.DEFAULT_BOARD_NAME, language] }

    private val board by lazy { findDefaultBoard() }

    private lateinit var language: String

    private val member: Member = retrieveMe()

    private val statusLists = mutableMapOf<TaskStatus, TrelloList>()

    override fun Board.getReadableTasks(status: TaskStatus, comparator: Comparator<Task>?): List<Task> {
        return getTasks(status.getReadableListNames(language), comparator)
    }

    override fun Board.getPreviousTasks(status: TaskStatus, comparator: Comparator<Task>?): List<Task> {
        return getTasks(status.getPreviousListNames(language), comparator)
    }

    /** Sorted tasks (closer deadline first, in other case prevails Trello board list order) **/
    private fun Board.getTasks(listNames: Map<String, TaskStatus>, comparator: Comparator<Task>?): List<Task> {
        val selectedLists = board.getLists(listsWithCards()).filter(listNames)
        val tasks = mutableListOf<Task>()
        selectedLists.forEach {
            val listStatus = listNames[it.name!!.toLowerCase(language.toLocale())]!!
            statusLists[listStatus] = it
            tasks.addAll(it.cards!!.map { it.toTask(listStatus) })
        }
        return comparator.letOrElse(tasks) { tasks.sortedWith(it) }
    }

    override fun Board.addTask(task: Task): Task {
        val statusList = board.getLists(openListsWithName()).withStatus(task.status)
        val card = statusList.newCard(task.title, task.deadline?.atZone(zoneId), parameters("desc" to task.description, "pos" to "top"))
        return card.toTask(task.status)
    }

    override fun Board.moveTask(trelloTask: Task, to: TaskStatus) {
        statusLists[to]!!.moveCard(trelloTask.id) // Requires getTasks called before
    }

    override fun Board.archiveTask(trelloTask: Task) {
        trello.archiveCard(trelloTask.id) // Requires getTasks called before
    }

    fun getDefaultBoard() = board

    fun getMe() = member

    private fun List<TrelloList>.filter(names: Map<String, TaskStatus>): List<TrelloList> {
        val locale = language.toLocale()
        return filter { it.name!!.toLowerCase(locale) in names }
    }

    private fun List<TrelloList>.withStatus(status: TaskStatus): TrelloList {
        val statusListName = status.getListName(language)
        return firstOrNull { it.name!!.equals(statusListName, ignoreCase=true) }
                ?: board.newList(i18n[status.idNameList, language], parameters("pos" to "bottom"))
    }

    private fun Board.openListsWithName() = parameters("filter" to "open", "fields" to "name")

    private fun Board.listsWithCards() = openListsWithName().add("cards" to "open", "card_fields" to "name,desc,due,labels,shortUrl")

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
        val trelloLocaleIsSupported = language in i18n
        val board = trello.newBoard(defaultBoardName, parameters(
                "defaultLists" to "$trelloLocaleIsSupported",
                "powerUps" to "cardAging",
                "prefs_cardAging" to "regular",
                "desc" to i18n[S.DEFAULT_BOARD_DESCRIPTION, language]))
        board.enablePowerUp(PowerUp.CARD_AGING.id)
        if (!trelloLocaleIsSupported) {
            val bottom = parameters("pos" to "bottom")
            board.newList(i18n[S.TODO, language], bottom)
            board.newList(i18n[S.DOING, language], bottom)
            board.newList(i18n[S.DONE, language], bottom)
        }
        board.newList(i18n[S.SOMEDAY, language], parameters("pos" to "top"))
        tasksListener.onNewBoard(board)
        return board
    }

    private fun Card.toTask(status: TaskStatus) = Task(name!!, desc!!, status, due?.toLocalDateTime(), shortUrl, listOf(), id)

}