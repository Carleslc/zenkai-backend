package ai.zenkai.zenkai.services.clock

import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.zone.ZoneRulesException

const val DEFAULT_TIMEZONE_ID = "UTC"

@Service
class ClockService {

    private val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun getCurrentTime(timezone: ZoneId) = Time(formatter.format(LocalTime.now(timezone)), timezone)

    @Throws(DateTimeException::class, ZoneRulesException::class)
    fun getCurrentTime(timezone: String) = getCurrentTime(timezone.toZoneIdOrThrow())

    fun format(time: LocalTime): String = formatter.format(time)

}

@Throws(DateTimeException::class, ZoneRulesException::class)
fun String.toZoneIdOrThrow(): ZoneId = ZoneId.of(this)