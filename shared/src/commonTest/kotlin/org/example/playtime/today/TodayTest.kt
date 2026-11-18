package org.example.playtime.today

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.example.playtime.api.DailySummariesResponse
import org.example.playtime.api.NintendoJson
import org.example.playtime.api.ParentalControlSettingResponse

/** Synthetic fixtures shaped like the API's responses. */
class TodayTest {
    private fun setting(extra: String = "null", timerMode: String = "EACH_DAY_OF_THE_WEEK", wednesdayAllowance: String = """{"enabled":true,"limitTime":0}""") =
        NintendoJson.decodeFromString(
            ParentalControlSettingResponse.serializer(),
            """
            {"parentalControlSetting":{"deviceId":"DEV","playTimerRegulations":{
               "timerMode":"$timerMode","restrictionMode":"FORCED_TERMINATION",
               "dailyRegulations":{"timeToPlayInOneDay":{"enabled":true,"limitTime":45},"bedtime":{"enabled":true,"endingTime":{"hour":21,"minute":0},"startingTime":{"hour":7,"minute":0}}},
               "eachDayOfTheWeekRegulations":{
                 "tuesday":{"timeToPlayInOneDay":{"enabled":true,"limitTime":30},"bedtime":{"enabled":true,"endingTime":{"hour":16,"minute":0},"startingTime":{"hour":6,"minute":0}}},
                 "wednesday":{"timeToPlayInOneDay":$wednesdayAllowance,"bedtime":{"enabled":true,"endingTime":{"hour":19,"minute":0},"startingTime":{"hour":6,"minute":0}}}}},
               "etag":"e"},
             "ownedDevice":{"device":{"timeZone":"Europe/Paris","extraPlayingTime":$extra}}}
            """,
        )

    private val summaries = NintendoJson.decodeFromString(
        DailySummariesResponse.serializer(),
        """{"dailySummaries":[{"date":"2030-01-02","result":"CALCULATING","playingTime":25},{"date":"2030-01-01","playingTime":40}],"lastUpdatedAt":1893604800}""",
    )

    // 2030-01-02 (a Wednesday) 17:30 in Paris = 16:30 UTC
    private val wednesday1730 = Instant.parse("2030-01-02T16:30:00Z")

    @Test
    fun usesTheConsoleTimeZoneAndWeekday() {
        val t = buildToday(setting(), summaries, "Europe/Paris", wednesday1730)
        assertEquals(17 * 60 + 30, t.state.now)
        assertEquals(19 * 60, t.state.configuredBedtime)
        assertEquals(0, t.state.limit)
        assertEquals(25, t.state.played) // today's summary, not yesterday's
        assertNull(t.state.extension)
        assertEquals(Instant.fromEpochSeconds(1893604800), t.playedAsOf)
    }

    @Test
    fun sameInstantIsStillTuesdayInAnEarlierZone() {
        val t = buildToday(setting(), summaries, "America/New_York", Instant.parse("2030-01-02T03:00:00Z"))
        assertEquals(16 * 60, t.state.configuredBedtime) // Tuesday 22:00 in New York
        assertEquals(30, t.state.limit)
        assertEquals(40, t.state.played) // the 2030-01-01 summary: still that date in New York
    }

    @Test
    fun readsTodaysExtensionAndExtendedBedtime() {
        val extra = """{"inOneDay":{"duration":40,"isInfinity":false},"bedtime":{"endTime":{"hour":19,"minute":40}},"expiresAt":${wednesday1730.epochSeconds + 3600}}"""
        val t = buildToday(setting(extra), summaries, "Europe/Paris", wednesday1730)
        assertEquals(40, t.state.extension)
        assertEquals(19 * 60 + 40, t.state.extendedBedtime)
    }

    @Test
    fun extensionWithoutBedtimeKeepsTheSetting() {
        val extra = """{"inOneDay":{"duration":5,"isInfinity":false},"bedtime":null,"expiresAt":${wednesday1730.epochSeconds + 3600}}"""
        val t = buildToday(setting(extra), summaries, "Europe/Paris", wednesday1730)
        assertEquals(5, t.state.extension)
        assertNull(t.state.extendedBedtime)
    }

    @Test
    fun expiredExtensionIsIgnored() {
        val extra = """{"inOneDay":{"duration":40,"isInfinity":false},"bedtime":null,"expiresAt":${wednesday1730.epochSeconds - 1}}"""
        assertNull(buildToday(setting(extra), summaries, "Europe/Paris", wednesday1730).state.extension)
    }

    @Test
    fun unlimitedAndDisabledAreNeverReached() {
        val infinite = """{"inOneDay":{"duration":null,"isInfinity":true},"bedtime":null,"expiresAt":null}"""
        assertEquals(NONE_TODAY, buildToday(setting(infinite), summaries, "Europe/Paris", wednesday1730).state.extension)
        val noAllowance = buildToday(setting(wednesdayAllowance = """{"enabled":false,"limitTime":null}"""), summaries, "Europe/Paris", wednesday1730)
        assertEquals(NONE_TODAY, noAllowance.state.limit)
        assertFalse(noAllowance.allowanceEnabled)
    }

    @Test
    fun dailyModeUsesTheSharedSetting() {
        val t = buildToday(setting(timerMode = "DAILY"), summaries, "Europe/Paris", wednesday1730)
        assertEquals(45, t.state.limit)
        assertEquals(21 * 60, t.state.configuredBedtime)
    }

    @Test
    fun flagsTheTimeBeforeWakeUp() {
        assertTrue(buildToday(setting(), summaries, "Europe/Paris", Instant.parse("2030-01-02T04:40:00Z")).beforeWakeUp) // 05:40 Paris
        assertFalse(buildToday(setting(), summaries, "Europe/Paris", wednesday1730).beforeWakeUp)
    }
}
