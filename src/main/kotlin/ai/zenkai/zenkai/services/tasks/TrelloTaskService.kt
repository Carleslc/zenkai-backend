package ai.zenkai.zenkai.services.tasks

import ai.zenkai.zenkai.model.Task
import ai.zenkai.zenkai.model.TaskStatus
import com.julienvey.trello.Trello
import me.carleslc.kotlin.extensions.time.days
import me.carleslc.kotlin.extensions.time.fromNow
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.social.connect.Connection
import org.springframework.stereotype.Service

@Service
class TrelloTaskService : TaskService {

    private val trello by lazy { (SecurityContextHolder.getContext().authentication.credentials as Connection<*>).api as Trello }

    /** Sorted tasks (closer deadline first, in other case prevails Trello board list order) **/
    override fun getTasks(token: String, status: TaskStatus): List<Task> {
        return listOf(Task("Tarea de ejemplo", "¡Vamos \uD83E\uDD42!", TaskStatus.TODO, 5 days fromNow, listOf("EXAMPLE")))
    }

}