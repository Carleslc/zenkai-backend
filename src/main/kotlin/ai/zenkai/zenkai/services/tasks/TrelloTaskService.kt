package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import me.carleslc.kotlin.extensions.time.days
import me.carleslc.kotlin.extensions.time.fromNow
import org.springframework.stereotype.Service

@Service
class TrelloTaskService : TaskService {

    /** Sorted tasks (closer deadline first, in other case prevails Trello board list order) **/
    override fun getTasks(token: String, status: TaskStatus): List<Task> {
        return listOf(Task("Tarea de ejemplo", "¡Vamos \uD83E\uDD42!", TaskStatus.TODO, 5 days fromNow, listOf("EXAMPLE")))
    }

}