package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.services.Service
import ai.zenkai.zenkai.services.tasks.trello.Board

interface TaskService : Service {

    fun Board.getReadableTasks(status: TaskStatus, comparator: Comparator<Task>? = Task.priorityComparator()): List<Task>

    fun Board.getPreviousTasks(status: TaskStatus = TaskStatus.DONE, comparator: Comparator<Task>? = Task.statusComparator()): List<Task>

    fun Board.addTask(task: Task): Task

    fun Board.moveTask(trelloTask: Task, to: TaskStatus)

    fun Board.archiveTask(trelloTask: Task)

}