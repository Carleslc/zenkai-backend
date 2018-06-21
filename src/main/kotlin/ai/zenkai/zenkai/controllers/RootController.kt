package ai.zenkai.zenkai.controllers

import ai.zenkai.zenkai.*
import ai.zenkai.zenkai.exceptions.badRequest
import ai.zenkai.zenkai.exceptions.multicatch
import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.trimStopWordsLeft
import ai.zenkai.zenkai.model.*
import ai.zenkai.zenkai.model.TaskStatus.*
import ai.zenkai.zenkai.services.calendar.*
import ai.zenkai.zenkai.services.clock.ClockService
import ai.zenkai.zenkai.services.clock.isSingleHour
import ai.zenkai.zenkai.services.weather.WeatherService
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import me.carleslc.kotlin.extensions.html.h
import me.carleslc.kotlin.extensions.standard.isNotNull
import me.carleslc.kotlin.extensions.standard.isNull
import me.carleslc.kotlin.extensions.strings.isNotNullOrBlank
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class RootController(private val clockService: ClockService,
                     private val calendarService: CalendarService,
                     private val weatherService: WeatherService) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Autowired
    lateinit var gson: Gson

    @GetMapping("/")
    fun root() = "Hello, I'm Zenkai!".h(1)

    @PostMapping("/")
    fun intentMapper(req: HttpServletRequest, res: HttpServletResponse) {
        try {
            Bot.handleRequest(CachedHttpServletRequest(req), res, gson, actionMap, calendarService, clockService)
        } catch (e: Exception) {
            e.multicatch(IllegalStateException::class, JsonSyntaxException::class) {
                badRequest(e, gson, res)
            }
        }
    }

    fun Bot.sum() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a + b

        addMessage(get(S.SUM).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.substract() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a - b
        addMessage(get(S.SUBSTRACT).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.multiply() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        val result = a * b
        addMessage(get(S.MULTIPLY).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
    }

    fun Bot.divide() {
        val a = getDouble("number1")
        val b = getDouble("number2")
        if (b == 0.toDouble()) {
            addMessage(S.DIVIDE_ZERO)
        } else {
            val result = a / b
            addMessage(get(S.DIVIDE).replace("\$1" to fixInt(a), "\$2" to fixInt(b), "\$3" to fixInt(result)))
        }
    }

    fun Bot.weather() {
        val location = getString("city")
        fill(weatherService.getWeather(location!!, language), get(S.CITY_NOT_FOUND)) {
            get(S.WEATHER)
                    .replace("\$city", city)
                    .replace("\$temperature", temperature.toString())
                    .replace("\$description", description)
        }
    }

    fun Bot.clock() {
        val time = clockService.now(timezone)
        val prefix = get(if (time.isSingleHour()) S.CURRENT_TIME_SINGLE else S.CURRENT_TIME)
        val formattedTime = clockService.pretty12(time, language)
        val speech = "${prefix.capitalize()} $formattedTime".trim()
        addMessage(speech, formattedTime)
    }

    fun Bot.calendarPeriod() {
        val today = calendarService.today(timezone)
        val dayOfWeek = getString("day-of-week")?.let { DayOfWeek.valueOf(it) }
        val period = getDate("period")?.let { DatePeriod(it, calendarService) }
        val original = getString("period-original")?.toLowerCase(locale).orEmpty()

        val date = calendarService.inPeriod(dayOfWeek ?: today.dayOfWeek,
                period ?: DatePeriod.default(calendarService, timezone), today)

        val format = if (dayOfWeek.isNull()) {
            calendarService.prettyDate(date, language)
        } else {
            calendarService.getDayOfMonth(date, language)
        }

        val originalString = original.clean().capitalize()
        var be = isOrWas(date)
        if (originalString.isBlank()) be = be.capitalize()
        addMessage("$originalString $be $format".trim())
    }

    fun Bot.calendar() {
        val today = calendarService.today(timezone)
        var date = getDate("date") ?: today
        val original = getString("date-original")?.toLowerCase(locale) ?: get(S.TODAY)
        val dateAsk = getString("date-ask")?.clean()?.toLowerCase(locale)
        val isAsking = dateAsk.isNotNullOrBlank()

        fun processAskingOr(notAsking: () -> String): String {
            return if (isAsking) {
                if (calendarService.isDayOfWeek(dateAsk!!, language)) {
                    logger.info("Asking Day of Week")
                    calendarService.getDayOfWeek(date, language)
                } else {
                    logger.info("Asking Day of Month")
                    calendarService.getDayOfMonth(date, language)
                }
            } else notAsking()
        }

        val queryDayOfWeek = calendarService.isDayOfWeek(original, language)

        if (calendarService.isPast(query, language) && date >= today) {
            date = if (queryDayOfWeek) date.minusWeeks(1) else date.minusYears(1)
        }

        val format = when {
            queryDayOfWeek -> {
                logger.info("Query Day of Week")
                processAskingOr { calendarService.getDayOfMonth(date, language) }
            }
            calendarService.isDayOfMonth(original, language) -> {
                logger.info("Query Day of Month")
                processAskingOr { calendarService.getDayOfWeek(date, language) }
            }
            else -> processAskingOr { calendarService.prettyDate(date, language) }
        }

        addMessage("${original.clean().capitalize()} ${isOrWas(date)} $format")
    }

    private fun Bot.addTaskMessage(status: TaskStatus, size: Int) {
        val messageId = when (status) {
            DONE -> when {
                size == 0 -> S.EMPTY_DONE
                size == 1 -> S.COMPLETED_FIRST_TASK
                size < 5 -> S.COMPLETED_TASKS
                size < 20 -> S.COMPLETED_TASKS_CONGRATULATIONS
                else -> S.COMPLETED_TASKS_KEEP_IT_UP
            }
            DOING -> when (size) {
                0 -> S.EMPTY_DOING
                1 -> S.DOING_TASK
                else -> S.MULTITASKING
            }
            TODO -> when (size) {
                0 -> S.EMPTY_TODO
                1 -> S.TODO_SINGLE
                else -> S.TODO_FOCUS
            }
            SOMEDAY -> when (size) {
                0 -> S.EMPTY_SOMEDAY
                1 -> S.SOMEDAY_SINGLE
                else -> S.SOMEDAY_TASKS
            }
        }
        addMessage(get(messageId).replace("\$size", size.toString()))
    }

    private fun Bot.checkSomedayWarning(todoTasksSize: Int, somedayTasksSize: () -> Int) {
        if (todoTasksSize > 10 && somedayTasksSize() == 0) {
            addMessage(S.EMPTY_SOMEDAY)
        }
    }

    private fun Bot.checkMultitasking(doingTasksSize: Int) {
        if (doingTasksSize > 1) {
            addMessage(get(S.MULTITASKING).replace("\$size", doingTasksSize.toString()))
        }
    }

    fun Bot.readTasks() = withTasks {
        val taskType = getString("task-type")
        logger.info("Task Type $taskType")
        val status = TaskStatus.parse(taskType)
        with (getReadableTasks(status, Task.statusComparator())) {
            addTaskMessage(status, size)
            if (status == TODO) {
                val doingTasksSize = count { it.status == DOING }
                checkMultitasking(doingTasksSize)
                checkSomedayWarning(size - doingTasksSize, { getReadableTasks(SOMEDAY).size })
            }
            if (isNotEmpty()) {
                addMessage(get(if (size == 1) S.YOUR_TASK else S.YOUR_TASKS))
                forEach { addTask(it) }
            }
        }
    }

    private fun Bot.askTaskDuration(): Duration? {
        var askDuration = false
        var duration = getDuration("duration")
        if (duration == null) {
            val queryDuration = clockService.extractDuration(query)
            if (!queryDuration.isZero) {
                duration = queryDuration
            } else if (!isContext(TASK_ASK_DURATION_CONTEXT)) {
                setContext(TASK_ASK_DURATION_CONTEXT)
                addMessage(S.ASK_TASK_DURATION)
                askDuration = true
            } else {
                duration = Duration.of(1, ChronoUnit.HOURS)
            }
        }
        if (!askDuration) {
            resetContext(TASK_ASK_DURATION_CONTEXT)
        }
        return duration
    }

    fun Bot.addTask() = withTasks {
        val title = getString("task-title", TASK_ADD_CONTEXT)
        val cancelled = isCancelled(query)
        if (cancelled) {
            resetContext(TASK_ASK_DURATION_CONTEXT)
        } else if (title != null) {
            val status = TaskStatus.parse(getString("task-type", TASK_ADD_CONTEXT))
            val tasks = getAllTasks(null)
            val words = title.words(locale).toList()
            var wordsWithoutFirst = words
            val titleSplit = if (words.size > 1) {
                wordsWithoutFirst = words.subList(1, words.size)
                wordsWithoutFirst.joinToString(" ")
            } else title
            val trimmedTitle = wordsWithoutFirst.trimStopWordsLeft(locale)
            val titleMatch = if (trimmedTitle.isNotBlank()) trimmedTitle else titleSplit
            val matchTask = tasks.find { it.hasSimilarTitle(titleMatch, locale) }
            val alreadyAdded = matchTask?.status == status
            var task: Task
            val messageId: S
            var duration: Duration? = null
            var movedFrom: TaskStatus? = null
            when {
                alreadyAdded -> {
                    task = matchTask!!
                    messageId = S.ALREADY_ADDED
                }
                matchTask != null -> { // move task
                    task = matchTask
                    messageId = S.MOVED_TASK
                    movedFrom = task.status
                    moveTask(task, status)
                }
                else -> { // new task
                    duration = askTaskDuration()
                    if (duration != null) {
                        val minutes = duration.toMinutes()
                        if (minutes <= 2) {
                            addMessage(S.TWO_MINUTES_WARNING)
                        } else if (duration.toHours() > 8) {
                            addMessage(S.TASK_DURATION_WARNING)
                        }
                        val deviation = 1.3 // 30% estimation deviation
                        duration = Duration.of(maxOf(minutes, (minutes * deviation).roundToTenth()), ChronoUnit.MINUTES)
                        var deadline = getDateTime("date", "time", context = TASK_ADD_CONTEXT)
                        if (deadline != null && deadline < ZonedDateTime.now(timezone)) {
                            deadline = getDateTime("date", "time", context = TASK_ADD_CONTEXT, implicit = false)
                        }
                        task = Task(words.trimStopWordsLeft(locale).capitalize(), "", status, duration, deadline?.toLocalDateTime())
                        task = createTask(task)
                        messageId = S.ADDED_TASK
                    } else return@withTasks
                }
            }
            addMessage(get(messageId).replace("\$type", status.getListName(language).capitalize()))
            if (!alreadyAdded) {
                if (status == TODO) {
                    checkSomedayWarning(1 + tasks.count { it.status == TODO }, { tasks.count { it.status == SOMEDAY } })
                } else if (status == DOING) {
                    checkMultitasking(1 + tasks.count { it.status == DOING })
                } else if (status == DONE) {
                    val doneTasksSize = 1 + tasks.count { it.status == DONE }
                    if (doneTasksSize == 1 || doneTasksSize % 5 == 0) {
                        addTaskMessage(DONE, doneTasksSize)
                    }
                }
            }
            setArgument("moved-from", movedFrom?.toString())
            addMessage(S.YOUR_TASK)
            addTask(task)
        }
    }

    private fun Bot.tryDeleteTaskOr(notFoundBlock: Bot.() -> Unit = {}) = withTasks {
        val title = getString("title")
        if (title != null) {
            val tasks = getAllTasks(comparator = compareBy<Task> { it.title.length })
            val task = tasks.find { it.hasSimilarTitle(title, locale) }
            if (task != null) {
                archiveTask(task)
                addMessage(S.TASK_DELETED)
                addMessage(S.YOUR_TASK)
                addTask(task)
            } else {
                notFoundBlock()
            }
        }
    }

    private fun Bot.tryDeleteEventOr(notFoundBlock: Bot.() -> Unit = {}) = withEvents {
        val title = getString("title")
        if (title != null) {
            val event = findEvent(title.capitalize())
            if (event != null) {
                removeEvent(event)
                addMessage(S.EVENT_DELETED)
                addMessage(S.YOUR_EVENT)
                addEvent(event)
            } else {
                notFoundBlock()
            }
        }
    }

    fun Bot.deleteTask() = tryDeleteTaskOr {
        tryDeleteEventOr {
            addMessage(S.TASK_NOT_FOUND)
        }
    }

    fun Bot.deleteEvent() = tryDeleteEventOr {
        tryDeleteTaskOr {
            addMessage(S.EVENT_NOT_FOUND)
        }
    }

    private fun getEventsDateMessageId(events: List<Event>): S {
        return when {
            events.isEmpty() -> S.NO_EVENTS_DATE
            events.size == 1 -> S.SINGLE_EVENT_DATE
            else -> S.YOUR_EVENTS_DATE
        }
    }

    fun Bot.readEvents() = withEvents {
        val date = getDate("date", context = TASK_READ_CONTEXT)
        val events = if (date != null) {
            readEvents(date)
        } else {
            readFollowingEvents(3, maxDate = ZonedDateTime.now(timezone).plusWeeks(1).toLocalDateTime())
        }
        val messageId = if (date.isNotNull()) {
            getEventsDateMessageId(events)
        } else when {
            events.isEmpty() -> S.NO_EVENTS
            events.size == 1 -> S.SINGLE_EVENT
            else -> S.YOUR_EVENTS
        }
        var message = get(messageId).replace("\$size", events.size.toString())
        if (date != null) {
            message = message.replace("\$date", calendarService.prettyDate(date, language))
        }
        addMessage(message)
        events.forEach { addEvent(it) }
    }

    private fun Bot.putEvent(from: ZonedDateTime?, to: ZonedDateTime?,
                             endTimeSpecified: Boolean,
                             startDateOriginal: String? = getString("start-date-original"),
                             endDateOriginal: String? = getString("end-date-original"),
                             startTimeOriginal: String? = getString("start-time-original"),
                             endTimeOriginal: String? = getString("end-time-original")) = withEvents {
        var start = from
        var end = to
        val now = ZonedDateTime.now(timezone)!!
        logger.info("Now: $now")
        var title = getString("event-title")
        val location = getString("location")
        logger.info("Title: $title")
        logger.info("Start: $start (Original $startDateOriginal / $startTimeOriginal)")
        logger.info("End:   $end (Original $endDateOriginal / $endTimeOriginal)")
        if (title != null && start != null && end != null && !isCancelled(query)) {
            title = title.capitalize()
            start = calendarService.implicitDateTime(now, start, startDateOriginal, startTimeOriginal, language)
            end = calendarService.implicitDateTime(now, end, endDateOriginal ?: startDateOriginal, endTimeOriginal, language)
            if (start < now.minusMinutes(1)) {
                logger.info("Start < now")
                if (start.toLocalDate() < now.toLocalDate()) {
                    logger.info("Start Date < Today")
                    start = now.toLocalDate().atTime(start.toLocalTime()).atZone(timezone)!!
                }
                start = start.shiftTime(now)
            }
            if (end.toLocalDate() < start.toLocalDate()) {
                logger.info("End Date < Start Date")
                end = start.toLocalDate().atTime(end.toLocalTime()).atZone(timezone)!!
            }
            if (!endTimeSpecified && start.toLocalDate() == end.toLocalDate()) {
                logger.info("end-time not specified -> end = start + 1h")
                end = end.toLocalDate().atTime(start.toLocalTime()).plusHours(1).atZone(timezone)!!
            }
            if (end <= start) {
                logger.info("End Time <= Start Time")
                val endMorning = calendarService.isMorningNullable(endTimeOriginal, language)
                if (endMorning || calendarService.isMorningNullable(startTimeOriginal, language)) {
                    start = start.shiftTimeBack(end).shiftTime(now)
                }
                logger.info("End $end shift Start $start, morning $endMorning")
                end = end.shiftTime(start, endMorning)
            }
            logger.info("Start Finish: $start")
            logger.info("End Finish:   $end")
            val overlapping = getEvents(start.toLocalDateTime(), end.toLocalDateTime())
            if (overlapping.isEmpty()) {
                addMessage(S.ADDED_EVENT)
            } else {
                addMessage(get(S.OVERLAPPING_EVENTS).replace("\$title", title))
                overlapping.forEach { addEvent(it) }
            }
            val event = createEvent(Event(title, start, end, "", location))
            addMessage(S.YOUR_EVENT)
            addEvent(event)
        }
    }

    fun Bot.addEvent(implicitToday: Boolean = false) {
        val start = getDateTime("start-date", "start-time",
                defaultDate = if (implicitToday) calendarService.today(timezone) else null,
                defaultTime = if (implicitToday) clockService.now(timezone) else null)
        val end = getDateTime("end-date", "end-time", implicit = false, defaultDate = start?.toLocalDate())
        putEvent(start, end, getTime("end-time") != null)
    }

    fun Bot.addPeriodEvent() {
        val datePeriod = getDatePeriod("date-period")
        val datePeriodOriginal = getString("date-period-original")
        putEvent(datePeriod?.start?.atStartOfDay(timezone), datePeriod?.end?.atStartOfDay(timezone),
                true, datePeriodOriginal, datePeriodOriginal)
    }

    fun Bot.addQuickEvent() = withEvents {
        try {
            val event = createEvent(query)
            addMessages(S.ADDED_EVENT, S.YOUR_EVENT)
            addEvent(event)
        } catch (e: GoogleJsonResponseException) {
            addMessage(S.CANNOT_ADD_EVENT)
        }
    }

    fun Bot.rollbackEventAdd() = tryDeleteEventOr()

    fun Bot.rollbackTaskAdd() {
        val moved = getString("moved-from")
        val title = getString("title")
        if (moved != null && title != null) {
            withTasks {
                val status = TaskStatus.valueOf(moved)
                val tasks = getAllTasks(null)
                val task = tasks.find { it.hasSimilarTitle(title, locale) }
                if (task != null) {
                    val messageId = if (task.status == status) {
                        S.ALREADY_ADDED
                    } else {
                        moveTask(task, status)
                        setArgument("moved-from", task.status.toString())
                        S.MOVED_TASK
                    }
                    addMessage(get(messageId).replace("\$type", status.getListName(language).capitalize()))
                    addMessage(S.YOUR_TASK)
                    addTask(task)
                }
            }
        } else tryDeleteTaskOr()
    }

    fun Bot.schedule() = withTasksEvents { taskService, eventService ->
        val date = getDate("date")!!
        val now = ZonedDateTime.now(timezone)

        if (date.isBefore(now.toLocalDate())) {
            addMessage(S.PAST_SCHEDULE_DATE)
            return@withTasksEvents
        }

        val period = getTimePeriod("start", "end", "time-period",false)
        val startTime = period?.start ?: LocalTime.of(8, 0)
        val endTime = period?.end ?: LocalTime.of(21, 0)

        val start = date.atTime(startTime).shiftToday(now).toLocalDateTime()
        val end = date.atTime(endTime).shiftToday(now).toLocalDateTime()

        logger.info("Scheduling tasks from $start to $end")

        val scheduler = Scheduler(taskService, eventService, language)
        val (scheduledEvents, dateEvents) = scheduler.schedule(start, end, timezone)

        val events = mutableListOf<Event>().also {
            it.addAll(scheduledEvents)
            it.addAll(dateEvents)
        }.sortedBy { it.start }

        val messageId = when {
            scheduler.tasks.isEmpty() -> S.NO_TASKS_SCHEDULE
            scheduledEvents.isEmpty() -> S.NO_SCHEDULED
            scheduledEvents.size == 1 -> S.SCHEDULED_SINGLE
            else -> S.SCHEDULED
        }

        fun deadlineMissed(task: Task): Boolean {
            return task.deadline?.isBefore(end) == true && scheduledEvents.all { it.title != task.title || task.deadline.isAfter(it.end.toLocalDateTime()) == true }
        }

        val missedTasks = scheduler.tasks.filter(::deadlineMissed)
        if (missedTasks.isNotEmpty()) {
            addMessage(S.DEADLINE_MISSED_WARNING)
            missedTasks.forEach { addTask(it) }
        }
        addMessage(get(messageId).replace("\$size", scheduledEvents.size.toString()))
        if (scheduledEvents.isNotEmpty()) {
            addMessage(get(getEventsDateMessageId(events)).replace("\$size", events.size.toString()).replace("\$date", calendarService.prettyDate(date, language)))
            events.forEach { addEvent(it) }
        }
    }

    fun Bot.clearSchedule() = withEvents {
        removeEvents(get(S.AUTO_SCHEDULED_ID))
        addMessage(S.REMOVED_SCHEDULING)
    }

    val actionMap: Map<String, Handler> = mapOf(
            "calculator.sum" to { b -> b.sum() },
            "calculator.substraction" to { b -> b.substract() },
            "calculator.multiplication" to { b -> b.multiply() },
            "calculator.division" to { b -> b.divide() },
            "weather" to { b -> b.weather() },
            "greetings" to { b -> b.greetings() },
            "login" to { b -> b.login() },
            "logout" to { b -> b.logout() },
            "time.get" to { b -> b.clock() },
            "date.get" to { b -> b.calendar() },
            "date.get.period" to { b -> b.calendarPeriod() },
            "tasks.read" to { b -> b.readTasks() },
            "tasks.add" to { b -> b.addTask() },
            "tasks.add.rollback" to { b -> b.rollbackTaskAdd() },
            "tasks.delete" to { b -> b.deleteTask() },
            "tasks.schedule" to { b -> b.schedule() },
            "tasks.schedule.clear" to { b -> b.clearSchedule() },
            "events.read" to { b -> b.readEvents() },
            "events.add" to { b -> b.addEvent() },
            "events.add.now" to { b -> b.addEvent(implicitToday = true) },
            "events.add.period" to { b -> b.addPeriodEvent() },
            "events.add.quick" to { b -> b.addQuickEvent() },
            "events.add.rollback" to { b -> b.rollbackEventAdd() },
            "events.delete" to { b -> b.deleteEvent() }
    )

}