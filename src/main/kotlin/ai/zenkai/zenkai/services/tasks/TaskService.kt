package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import ai.zenkai.zenkai.services.Service

interface TaskService : Service {

    fun getTodoTasks(comparator: Comparator<Task>? = Task.deadlinePriorityComparator()): List<Task>

    fun getReadableTasks(status: TaskStatus, comparator: Comparator<Task>? = Task.deadlinePriorityComparator()): List<Task>

    fun getAllTasks(comparator: Comparator<Task>? = Task.statusComparator()): List<Task>

    fun createTask(task: Task): Task

    fun moveTask(task: Task, to: TaskStatus)

    fun archiveTask(task: Task)

    fun getDefaultBoard(): Board

}