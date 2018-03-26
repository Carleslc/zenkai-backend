package ai.zenkai.zenkai.model

import java.time.LocalDateTime

data class Task(val title: String,
                val description: String,
                val status: TaskStatus,
                val deadline: LocalDateTime? = null,
                val tags: List<String>)