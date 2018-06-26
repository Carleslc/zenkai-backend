package ai.zenkai.zenkai.i18n

import ai.zenkai.zenkai.cleanFormat
import ai.zenkai.zenkai.model.logger
import ai.zenkai.zenkai.words
import cue.lang.StopWords
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

    private val stopWords by lazy { mutableMapOf<String, StopWords>() }

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

    fun getStopWords(language: String): StopWords {
        if (language !in stopWords) {
            stopWords[language] = StopWords.values().first { it.language == language }.also(StopWords::loadLanguage)
        }
        return stopWords[language]!!
    }

}

enum class S {
    HELP,
    HELP_SPEECH,
    PAST,
    PREVIOUS,
    AGO,
    IS,
    WAS,
    OF,
    IN,
    AT,
    AND,
    CANCEL,
    CANCELLED,
    TIMEOUT,
    GREETINGS,
    LOGIN_TASKS,
    LOGIN_CALENDAR,
    LOGIN_TIMER,
    LOGOUT,
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
    ASK_TASK_DURATION,
    TASK_DURATION_WARNING,
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
    ESTIMATED_DURATION,
    NOW,
    UNTIL,
    UNTIL_SINGLE,
    UNIT_DAY,
    UNIT_HOUR,
    UNIT_MINUTE,
    UNIT_SECOND,
    NO_SCHEDULED,
    NO_TASKS_SCHEDULE,
    SCHEDULE,
    SCHEDULED,
    SCHEDULED_SINGLE,
    AUTO_SCHEDULED_ID,
    REMOVED_SCHEDULING,
    PAST_SCHEDULE_DATE,
    TWO_MINUTES_WARNING,
    DEADLINE_MISSED_WARNING,
    OVERLAPPING_EVENTS;
}

fun String.removeStopWords(locale: Locale): String {
    val stopWords = i18n.getStopWords(locale.language)
    return words(locale).filter { !stopWords.isStopWordExact(it.cleanFormat(locale)) }.joinToString(" ").trim()
}

fun String.trimStopWordsLeft(locale: Locale) = words(locale).toList().trimStopWordsLeft(locale)

fun List<String>.trimStopWordsLeft(locale: Locale): String {
    val stopWords = i18n.getStopWords(locale.language)
    val firstStopWords = takeWhile { stopWords.isStopWordExact(it.cleanFormat(locale)) }
    return subList(firstStopWords.size, size).joinToString(" ").trim()
}

fun StringBuilder.append(id: S, locale: String) = append(i18n[id, locale])

fun String.toLocale() = i18n[this]!!