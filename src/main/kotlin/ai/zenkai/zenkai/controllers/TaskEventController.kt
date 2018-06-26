package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.model.Bot
import ai.zenkai.zenkai.model.Handler
import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import org.springframework.stereotype.Controller

@Controller
class TaskEventController(private val eventController: EventController) : ActionController {

    override val actionMap: Map<String, Handler> = mapOf(
            "tasks.delete" to { b -> b.deleteTask() },
            "events.delete" to { b -> b.deleteEvent() },
            "tasks.add.rollback" to { b -> b.rollbackTaskAdd() },
            "events.add.rollback" to { b -> b.rollbackEventAdd() },
            "tasks.schedule" to { b -> b.schedule() },
            "tasks.schedule.clear" to { b -> b.clearSchedule() }
    )

    fun tryDeleteTaskOr(bot: Bot, notFoundBlock: Bot.() -> Unit = {}) = with(bot) {
        withTasks {
            val title = getString("title")
            if (title != null) {
                val tasks = getAllTasks(comparator = compareBy<Task> { it.title.length })
                val task = tasks.find { it.hasSimilarTitle(title, locale) }
                if (task != null) {
                    archiveTask(task)
                    addMessage(S.TASK_DELETED)
                    addMessage(S.YOUR_TASK)
                    addTask(task)
                } else {
                    notFoundBlock()
                }
            }
        }
    }

    fun Bot.rollbackTaskAdd() {
        val moved = getString("moved-from")
        val title = getString("title")
        if (moved != null && title != null) {
            withTasks {
                val status = TaskStatus.valueOf(moved)
                val tasks = getAllTasks(null)
                val task = tasks.find { it.hasSimilarTitle(title, locale) }
                if (task != null) {
                    val messageId = if (task.status == status) {
                        archiveTask(task)
                        setArgument("moved-from", null)
                        S.TASK_DELETED
                    } else {
                        moveTask(task, status)
                        setArgument("moved-from", task.status.toString())
                        S.MOVED_TASK
                    }
                    addMessage(get(messageId).replace("\$type", status.getListName(language).capitalize()))
                    addMessage(S.YOUR_TASK)
                    addTask(task)
                }
            }
        } else tryDeleteTaskOr(this)
    }

    fun Bot.deleteTask() = tryDeleteTaskOr(this) {
        tryDeleteEventOr(this) {
            addMessage(S.TASK_NOT_FOUND)
        }
    }

    fun Bot.rollbackEventAdd() = tryDeleteEventOr(this)

    fun tryDeleteEventOr(bot: Bot, notFoundBlock: Bot.() -> Unit = {}) = with(bot) {
        withEvents {
            val title = getString("title")
            if (title != null) {
                val event = findEvent(title.capitalize())
                if (event != null) {
                    removeEvent(event)
                    addMessage(S.EVENT_DELETED)
                    addMessage(S.YOUR_EVENT)
                    addEvent(event)
                } else {
                    notFoundBlock()
                }
            }
        }
    }

    fun Bot.deleteEvent() = tryDeleteEventOr(this) {
        tryDeleteTaskOr(this) {
            addMessage(S.EVENT_NOT_FOUND)
        }
    }

    fun Bot.schedule() = SchedulerController.schedule(this, eventController)

    fun Bot.clearSchedule() = withEvents {
        removeEvents(get(S.AUTO_SCHEDULED_ID))
        addMessage(S.REMOVED_SCHEDULING)
    }

}