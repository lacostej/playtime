package io.github.lacostej.playtime.today

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import io.github.lacostej.playtime.api.DailySummariesResponse
import io.github.lacostej.playtime.api.ParentalControlSettingResponse
import io.github.lacostej.playtime.planner.DayState

/** No allowance or no bedtime is modelled as "never reached today". */
const val NONE_TODAY = 24 * 60

/** Everything the screen needs about today, in the console's time zone. */
data class Today(
    val state: DayState,
    val timeZone: String,
    /** When Nintendo last updated the played time (it can lag about 10 minutes behind). */
    val playedAsOf: Instant?,
    /** Still inside last night's bedtime (before the wake-up time): extensions behave differently then. */
    val beforeWakeUp: Boolean,
    val allowanceEnabled: Boolean,
    val bedtimeEnabled: Boolean,
    /**
     * Last time the console was heard from: the later of its last play report and its last settings sync.
     * (device.lastOnlineCheckedAt is not usable: it showed a date years in the past on an active console.)
     */
    val consoleLastHeard: Instant?,
)

fun buildToday(
    setting: ParentalControlSettingResponse,
    summaries: DailySummariesResponse,
    consoleTimeZone: String?,
    now: Instant,
): Today {
    val zoneId = consoleTimeZone ?: setting.ownedDevice?.device?.timeZone
    val zone = zoneId?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
    val local = now.toLocalDateTime(zone)
    val minuteOfDay = local.hour * 60 + local.minute

    val timers = setting.parentalControlSetting.playTimerRegulations
    val regulation = if (timers.timerMode == "EACH_DAY_OF_THE_WEEK") {
        timers.eachDayOfTheWeekRegulations[local.dayOfWeek.name.lowercase()]
    } else {
        timers.dailyRegulations
    }
    val allowance = regulation?.timeToPlayInOneDay
    val bedtime = regulation?.bedtime
    val allowanceEnabled = allowance?.enabled == true
    val bedtimeEnabled = bedtime?.enabled == true && bedtime.endingTime != null

    // Today's extension, unless it has already expired (it ends at midnight).
    val extra = setting.ownedDevice?.device?.extraPlayingTime?.takeIf { e -> e.expiresAt == null || e.expiresAt > now.epochSeconds }
    val extension = extra?.inOneDay?.let { if (it.isInfinity) NONE_TODAY else it.duration }

    val played = summaries.dailySummaries.firstOrNull { it.date == local.date.toString() }?.playingTime ?: 0

    return Today(
        state = DayState(
            now = minuteOfDay,
            limit = if (allowanceEnabled) allowance.limitTime ?: 0 else NONE_TODAY,
            extension = extension,
            played = played,
            configuredBedtime = if (bedtimeEnabled) bedtime.endingTime.minutes else NONE_TODAY,
            extendedBedtime = extra?.bedtime?.endTime?.minutes,
        ),
        timeZone = zone.id,
        playedAsOf = summaries.lastUpdatedAt?.let { Instant.fromEpochSeconds(it) },
        beforeWakeUp = bedtimeEnabled && bedtime.startingTime != null && minuteOfDay < bedtime.startingTime.minutes,
        allowanceEnabled = allowanceEnabled,
        bedtimeEnabled = bedtimeEnabled,
        consoleLastHeard = listOfNotNull(
            summaries.lastUpdatedAt,
            setting.ownedDevice?.device?.synchronizedParentalControlSetting?.synchronizedAt,
        ).maxOrNull()?.let { Instant.fromEpochSeconds(it) },
    )
}
