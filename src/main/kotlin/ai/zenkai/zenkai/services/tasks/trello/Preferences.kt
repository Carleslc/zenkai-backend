package ai.zenkai.zenkai.services.tasks.trello

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Preferences(val sendSummaries: Boolean,
                       val minutesBetweenSummaries: Int,
                       val minutesBeforeDeadlineToNotify: Int,
                       val colorBlind: Boolean,
                       val locale: String)