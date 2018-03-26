package ai.zenkai.zenkai.model

enum class TaskStatus {
    SOMEDAY,
    TODO,
    DOING,
    DONE;

    fun isReadable(limit: TaskStatus) = this == limit || (this == DOING && limit == TODO)

    companion object {
        fun default() = TODO
    }

}