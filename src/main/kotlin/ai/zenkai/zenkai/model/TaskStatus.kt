package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import java.util.*

enum class TaskStatus(val idNameList: S) {
    SOMEDAY(S.SOMEDAY),
    TODO(S.TODO),
    DOING(S.DOING),
    DONE(S.DONE);

    fun getReadableListNamesLower(language: String): Map<String, TaskStatus> {
        val names = mutableMapOf(getListName(language) to this)
        if (this == TODO) {
            names[DOING.getListName(language)] = DOING
        }
        return names
    }

    fun getListName(language: String) = i18n[idNameList, language]

    companion object {
        fun default() = TODO

        fun parse(taskType: String?) = if (taskType != null) valueOf(taskType.toString()) else default()
    }

}