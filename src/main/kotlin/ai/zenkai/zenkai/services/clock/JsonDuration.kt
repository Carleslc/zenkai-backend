package ai.zenkai.zenkai.services.clock

import ai.zenkai.zenkai.i18n.S
import ai.zenkai.zenkai.i18n.i18n
import me.carleslc.kotlin.extensions.number.roundToLong
import java.time.Duration
import java.time.temporal.ChronoUnit

class JsonDuration(val amount: Double, val unit: String) {

    fun toDuration(language: String): Duration {
        var seconds = 0L
        when (unit) {
            i18n[S.UNIT_HOUR, language] -> seconds = 3600
            i18n[S.UNIT_MINUTE, language] -> seconds = 60
            i18n[S.UNIT_DAY, language] -> seconds = 24*3600
            i18n[S.UNIT_SECOND, language] -> seconds = 1
        }
        return Duration.of((seconds * amount).roundToLong(), ChronoUnit.SECONDS)
    }

}