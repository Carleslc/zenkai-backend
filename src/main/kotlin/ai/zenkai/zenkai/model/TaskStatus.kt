package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n

enum class TaskStatus(private val idNameList: S) {
    SOMEDAY(S.SOMEDAY),
    TODO(S.TODO),
    DOING(S.DOING),
    DONE(S.DONE);

    fun getListName(language: String) = i18n[idNameList, language]

    fun isReadable(limit: TaskStatus) = this == limit || (this == DOING && limit == TODO)

    companion object {
        fun default() = TODO
    }

}