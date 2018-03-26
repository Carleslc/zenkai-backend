package ai.zenkai.zenkai

import java.util.*

object i18n {

    private const val NAME = "strings/strings"
    private val DEFAULT_BUNDLE by lazy { languageBundles.values.first() }

    private val supportedLocales by lazy {
        arrayOf("en", "es").map {
            val locale = Locale(it)
            locale.language to locale
        }.toMap()
    }

    private val languageBundles by lazy {
        supportedLocales.map {
            it.key to ResourceBundle.getBundle(NAME, it.value)
        }.toMap()
    }

    operator fun contains(language: String) = supportedLocales.containsKey(language)

    operator fun get(id: S, locale: String): String {
        val bundle = languageBundles[locale] ?: DEFAULT_BUNDLE
        return bundle.getString(id.toString())
    }

    fun default() = supportedLocales.keys.first()

}

enum class S {
    CURRENT_TIME,
    CITY_NOT_FOUND,
    WEATHER,
    YOUR_TASKS,
    YOUR_TASK,
    TODO,
    TODO_SINGLE,
    EMPTY_TODO,
    EMPTY_DONE,
    EMPTY_DOING,
    COMPLETED_FIRST_TASK,
    COMPLETED_TASKS,
    COMPLETED_TASKS_CONGRATULATIONS,
    COMPLETED_TASKS_KEEP_IT_UP,
    DOING_TASK,
    MULTITASKING,
    DEADLINE,
    DEADLINE_SPEECH;
}

fun StringBuilder.append(id: S, locale: String) = append(i18n[id, locale])