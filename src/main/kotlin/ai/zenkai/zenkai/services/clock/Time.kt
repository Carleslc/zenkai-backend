package ai.zenkai.zenkai.services.clock

import java.time.ZoneId

data class Time(val time: String, @Transient private val zoneId: ZoneId) {
    val timezone = zoneId.id
}