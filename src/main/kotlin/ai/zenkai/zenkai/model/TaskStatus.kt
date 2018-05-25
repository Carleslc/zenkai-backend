package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import ai.zenkai.zenkai.i18n.toLocale

enum class TaskStatus(val idNameList: S) {
    SOMEDAY(S.SOMEDAY),
    TODO(S.TODO),
    DOING(S.DOING),
    DONE(S.DONE);

    fun getReadableListNames(language: String): Map<String, TaskStatus> {
        val names = mutableMapOf(getListName(language) to this)
        if (this == TODO) {
            names.add(DOING, language)
        }
        return names
    }

    fun getPreviousListNames(language: String): Map<String, TaskStatus> {
        val names = mutableMapOf(getListName(language) to this)
        when(this) {
            TODO -> names.add(SOMEDAY, language)
            DOING -> names.add(TODO, language)
            else -> { // DONE
                names.add(SOMEDAY, language)
                names.add(TODO, language)
                names.add(DOING, language)
            }
        }
        return names
    }

    fun getListName(language: String) = i18n[idNameList, language].toLowerCase(language.toLocale())

    private fun MutableMap<String, TaskStatus>.add(status: TaskStatus, language: String) {
        this[status.getListName(language)] = status
    }

    companion object {
        fun default() = TODO

        fun parse(taskType: String?) = if (taskType != null) valueOf(taskType.toString()) else default()
    }

}