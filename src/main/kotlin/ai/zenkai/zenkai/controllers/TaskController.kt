package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.LazyLogger
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.trimStopWordsLeft
import ai.zenkai.zenkai.model.*
import ai.zenkai.zenkai.roundToTenth
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.words
import org.slf4j.Logger
import org.springframework.stereotype.Controller
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Controller
class TaskController(private val clockService: ClockService) : ActionController {

    val logger: Logger by LazyLogger()

    override val actionMap: Map<String, Handler> = mapOf(
            "tasks.read" to { b -> b.readTasks() },
            "tasks.add" to { b -> b.addTask() }
    )

    private fun Bot.addTaskMessage(status: TaskStatus, size: Int) {
        val messageId = when (status) {
            TaskStatus.DONE -> when {
                size == 0 -> S.EMPTY_DONE
                size == 1 -> S.COMPLETED_FIRST_TASK
                size < 5 -> S.COMPLETED_TASKS
                size < 20 -> S.COMPLETED_TASKS_CONGRATULATIONS
                else -> S.COMPLETED_TASKS_KEEP_IT_UP
            }
            TaskStatus.DOING -> when (size) {
                0 -> S.EMPTY_DOING
                1 -> S.DOING_TASK
                else -> S.MULTITASKING
            }
            TaskStatus.TODO -> when (size) {
                0 -> S.EMPTY_TODO
                1 -> S.TODO_SINGLE
                else -> S.TODO_FOCUS
            }
            TaskStatus.SOMEDAY -> when (size) {
                0 -> S.EMPTY_SOMEDAY
                1 -> S.SOMEDAY_SINGLE
                else -> S.SOMEDAY_TASKS
            }
        }
        addMessage(get(messageId).replace("\$size", size.toString()))
    }

    private fun Bot.checkSomedayWarning(todoTasksSize: Int, somedayTasksSize: () -> Int) {
        if (todoTasksSize > 10 && somedayTasksSize() == 0) {
            addMessage(S.EMPTY_SOMEDAY)
        }
    }

    private fun Bot.checkMultitasking(doingTasksSize: Int) {
        if (doingTasksSize > 1) {
            addMessage(get(S.MULTITASKING).replace("\$size", doingTasksSize.toString()))
        }
    }

    fun Bot.readTasks() = withTasks {
        val taskType = getString("task-type")
        logger.info("Task Type $taskType")
        val status = TaskStatus.parse(taskType)
        with (getReadableTasks(status, Task.statusComparator())) {
            addTaskMessage(status, size)
            if (status == TaskStatus.TODO) {
                val doingTasksSize = count { it.status == TaskStatus.DOING }
                checkMultitasking(doingTasksSize)
                checkSomedayWarning(size - doingTasksSize, { getReadableTasks(TaskStatus.SOMEDAY).size })
            }
            if (isNotEmpty()) {
                addMessage(get(if (size == 1) S.YOUR_TASK else S.YOUR_TASKS))
                forEach { addTask(it) }
            }
        }
    }

    private fun Bot.askTaskDuration(): Duration? {
        var askDuration = false
        var duration = getDuration("duration")
        if (duration == null) {
            val queryDuration = clockService.extractDuration(query)
            if (!queryDuration.isZero) {
                duration = queryDuration
            } else if (!isContext(TASK_ASK_DURATION_CONTEXT)) {
                setContext(TASK_ASK_DURATION_CONTEXT)
                addMessage(S.ASK_TASK_DURATION)
                askDuration = true
            } else {
                duration = Duration.of(1, ChronoUnit.HOURS)
            }
        }
        if (!askDuration) {
            resetContext(TASK_ASK_DURATION_CONTEXT)
        }
        return duration
    }

    fun Bot.addTask() = withTasks {
        val title = getString("task-title", TASK_ADD_CONTEXT)
        val cancelled = isCancelled(query)
        if (cancelled) {
            resetContext(TASK_ASK_DURATION_CONTEXT)
        } else if (title != null) {
            val status = TaskStatus.parse(getString("task-type", TASK_ADD_CONTEXT))
            val tasks = getAllTasks(null)
            val titleWithoutDuration = clockService.removeDuration(title)
            val words = titleWithoutDuration.words(locale).toList()
            var wordsWithoutFirst = words
            val titleSplit = if (words.size > 1) {
                wordsWithoutFirst = words.subList(1, words.size)
                wordsWithoutFirst.joinToString(" ")
            } else titleWithoutDuration
            val trimmedTitle = wordsWithoutFirst.trimStopWordsLeft(locale)
            val titleMatch = if (trimmedTitle.isNotBlank()) trimmedTitle else titleSplit
            val matchTask = tasks.find { it.hasSimilarTitle(titleMatch, locale) }
            val alreadyAdded = matchTask?.status == status
            var task: Task
            val messageId: S
            var duration: Duration? = null
            var movedFrom: TaskStatus? = null
            when {
                alreadyAdded -> {
                    task = matchTask!!
                    messageId = S.ALREADY_ADDED
                }
                matchTask != null -> { // move task
                    task = matchTask
                    messageId = S.MOVED_TASK
                    movedFrom = task.status
                    moveTask(task, status)
                }
                else -> { // new task
                    duration = askTaskDuration()
                    if (duration != null) {
                        val minutes = duration.toMinutes()
                        if (minutes <= 2) {
                            addMessage(S.TWO_MINUTES_WARNING)
                        } else if (duration.toHours() > 8) {
                            addMessage(S.TASK_DURATION_WARNING)
                        }
                        val deviation = 1.3 // 30% estimation deviation
                        duration = Duration.of(maxOf(minutes, (minutes * deviation).roundToTenth()), ChronoUnit.MINUTES)
                        var deadline = getDateTime("date", "time", context = TASK_ADD_CONTEXT)
                        if (deadline != null && deadline < ZonedDateTime.now(timezone)) {
                            deadline = getDateTime("date", "time", context = TASK_ADD_CONTEXT, implicit = false)
                        }
                        task = Task(words.trimStopWordsLeft(locale).capitalize(), "", status, duration, deadline?.toLocalDateTime())
                        task = createTask(task)
                        messageId = S.ADDED_TASK
                    } else return@withTasks
                }
            }
            addMessage(get(messageId).replace("\$type", status.getListName(language).capitalize()))
            if (!alreadyAdded) {
                if (status == TaskStatus.TODO) {
                    checkSomedayWarning(1 + tasks.count { it.status == TaskStatus.TODO }, { tasks.count { it.status == TaskStatus.SOMEDAY } })
                } else if (status == TaskStatus.DOING) {
                    checkMultitasking(1 + tasks.count { it.status == TaskStatus.DOING })
                } else if (status == TaskStatus.DONE) {
                    val doneTasksSize = 1 + tasks.count { it.status == TaskStatus.DONE }
                    if (doneTasksSize == 1 || doneTasksSize % 5 == 0) {
                        addTaskMessage(TaskStatus.DONE, doneTasksSize)
                    }
                }
            }
            setArgument("moved-from", movedFrom?.toString())
            addMessage(S.YOUR_TASK)
            addTask(task)
        }
    }

}