package ai.zenkai.zenkai.i18n

import java.util.*

const val DEFAULT_LANGUAGE = "en"

object i18n : Set<String> by linkedSetOf(DEFAULT_LANGUAGE, "es") {

    private const val NAME = "strings/strings"
    private val DEFAULT_BUNDLE by lazy { languageBundles[DEFAULT_LANGUAGE]!! }

    private val supportedLocales by lazy {
        map {
            val locale = Locale(it)
            locale.language to locale
        }.toMap()
    }

    private val languageBundles by lazy {
        val utf8 = UTF8Control()
        supportedLocales.map {
            it.key to ResourceBundle.getBundle(NAME, it.value, utf8)
        }.toMap()
    }

    operator fun get(locale: String) = supportedLocales[locale]

    operator fun get(id: S, language: String): String {
        val bundle = languageBundles[language] ?: DEFAULT_BUNDLE
        return bundle.getString(id.toString())
    }

    fun getNonTranslatable(id: S) = get(id, DEFAULT_LANGUAGE)

    fun ensureLanguage(language: String): String {
        val lang = language.toLowerCase()
        return if (lang in this) lang else DEFAULT_LANGUAGE
    }

}

enum class S {
    PAST,
    PREVIOUS,
    AGO,
    IS,
    WAS,
    OF,
    IN,
    AT,
    AND,
    GREETINGS,
    LOGIN_TASKS,
    LOGIN_CALENDAR,
    LOGIN_TIMER,
    NAME,
    SUM,
    SUBSTRACT,
    DIVIDE,
    DIVIDE_ZERO,
    MULTIPLY,
    TODAY,
    YESTERDAY,
    TOMORROW,
    MORNING,
    CURRENT_TIME,
    CURRENT_TIME_SINGLE,
    CITY_NOT_FOUND,
    WEATHER,
    YOUR_TASKS,
    YOUR_TASK,
    SOMEDAY_SINGLE,
    SOMEDAY_TASKS,
    TODO_SINGLE,
    TODO_FOCUS,
    TODO,
    DOING,
    DONE,
    SOMEDAY,
    NEW_BOARD,
    NEW_CALENDAR,
    NO_EVENTS,
    NO_EVENTS_DATE,
    DEFAULT_BOARD_NAME,
    DEFAULT_BOARD_DESCRIPTION,
    EMPTY_SOMEDAY,
    EMPTY_TODO,
    EMPTY_DONE,
    EMPTY_DOING,
    COMPLETED_FIRST_TASK,
    COMPLETED_TASKS,
    COMPLETED_TASKS_CONGRATULATIONS,
    COMPLETED_TASKS_KEEP_IT_UP,
    DOING_TASK,
    MULTITASKING,
    ADDED_TASK,
    ADDED_EVENT,
    CANNOT_ADD_EVENT,
    ALREADY_ADDED,
    MOVED_TASK,
    TASK_NOT_FOUND,
    TASK_DELETED,
    EVENT_NOT_FOUND,
    EVENT_DELETED,
    DEFAULT_CALENDAR_NAME,
    DEFAULT_CALENDAR_DESCRIPTION,
    SINGLE_EVENT,
    YOUR_EVENT,
    YOUR_EVENTS,
    SINGLE_EVENT_DATE,
    YOUR_EVENTS_DATE,
    DEADLINE,
    DEADLINE_SPEECH,
    YEAR,
    YEARS,
    MONTH,
    MONTHS,
    DAY,
    DAYS,
    HOUR,
    HOURS,
    MINUTE,
    MINUTES,
    SECOND,
    SECONDS,
    DURATION,
    NOW,
    UNTIL,
    UNTIL_SINGLE;
}

fun StringBuilder.append(id: S, locale: String) = append(i18n[id, locale])

fun String.toLocale() = i18n[this]!!