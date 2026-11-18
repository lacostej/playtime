package org.example.playtime.api

import kotlinx.serialization.Serializable

// Only the fields the app uses; everything else is ignored (see NintendoJson).

@Serializable
data class HourMinute(val hour: Int, val minute: Int) {
    val minutes: Int get() = hour * 60 + minute
}

@Serializable
data class OwnedDevicesResponse(val ownedDevices: List<OwnedDevice> = emptyList())

@Serializable
data class OwnedDevice(val deviceId: String, val label: String? = null, val device: Device? = null)

@Serializable
data class Device(val timeZone: String? = null, val extraPlayingTime: ExtraPlayingTime? = null)

@Serializable
data class ExtraPlayingTime(val inOneDay: InOneDay? = null, val bedtime: ExtraBedtime? = null, val expiresAt: Long? = null)

@Serializable
data class InOneDay(val duration: Int? = null, val isInfinity: Boolean = false)

@Serializable
data class ExtraBedtime(val endTime: HourMinute? = null)

@Serializable
data class ParentalControlSettingResponse(
    val parentalControlSetting: ParentalControlSetting,
    val ownedDevice: DeviceWrapper? = null,
)

@Serializable
data class DeviceWrapper(val device: Device? = null)

@Serializable
data class ParentalControlSetting(val playTimerRegulations: PlayTimerRegulations)

@Serializable
data class PlayTimerRegulations(
    val timerMode: String? = null,
    val restrictionMode: String? = null,
    val dailyRegulations: DayRegulation? = null,
    val eachDayOfTheWeekRegulations: Map<String, DayRegulation> = emptyMap(),
)

@Serializable
data class DayRegulation(val timeToPlayInOneDay: TimeToPlay? = null, val bedtime: BedtimeSetting? = null)

@Serializable
data class TimeToPlay(val enabled: Boolean = false, val limitTime: Int? = null)

@Serializable
data class BedtimeSetting(val enabled: Boolean = false, val endingTime: HourMinute? = null, val startingTime: HourMinute? = null)

@Serializable
data class DailySummariesResponse(val dailySummaries: List<DailySummary> = emptyList(), val lastUpdatedAt: Long? = null)

@Serializable
data class DailySummary(val date: String, val playingTime: Int = 0)

@Serializable
data class ExtraPlayingTimeState(val status: String? = null, val extendedPlayingTime: Int? = null, val updatedAt: Long? = null)

enum class ExtraTimeStatus { TO_ADDED, TO_CANCELED }

@Serializable
internal data class UpdateExtraPlayingTimeRequest(val deviceId: String, val status: String, val additionalTime: Int? = null)

@Serializable
data class UpdateExtraPlayingTimeResponse(val status: String? = null, val nextStepDetail: NextStepDetail? = null, val updatedAt: Long? = null)

@Serializable
data class NextStepDetail(val estimatedBedtimeChanges: BedtimeChange? = null)

@Serializable
data class BedtimeChange(val from: String? = null, val to: String? = null)

@Serializable
internal data class ConfirmExtraPlayingTimeRequest(val deviceId: String, val additionalTime: Int, val withBedtime: Boolean)

@Serializable
internal data class ErrorBody(val errorCode: String? = null)
