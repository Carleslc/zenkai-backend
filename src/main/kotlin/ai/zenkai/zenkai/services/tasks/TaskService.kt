package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus

const val TASK_DISPLAY_LIMIT = 10

interface TaskService {

    fun getTasks(token: String, status: TaskStatus): List<Task>

}