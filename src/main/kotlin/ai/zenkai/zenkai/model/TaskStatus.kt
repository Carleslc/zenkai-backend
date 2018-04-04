package ai.zenkai.zenkai.model

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import java.util.*

enum class TaskStatus(private val idNameList: S) {
    SOMEDAY(S.SOMEDAY),
    TODO(S.TODO),
    DOING(S.DOING),
    DONE(S.DONE);

    fun getReadableListNamesLower(locale: Locale): Map<String, TaskStatus> {
        val names = mutableMapOf(getListName(locale) to this)
        if (this == TODO) {
            names[DOING.getListName(locale)] = DOING
        }
        return names
    }

    private fun getListName(locale: Locale) = i18n[idNameList, locale.language].toLowerCase(locale)

    companion object {
        fun default() = TODO

        fun parse(taskType: String?) = if (taskType != null) valueOf(taskType.toString()) else default()
    }

}