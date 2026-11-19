package io.github.lacostej.playtime.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import io.github.lacostej.playtime.FakeNintendo
import io.github.lacostej.playtime.json

class ActivityLogTest {
    @Test
    fun logLinesCarryNoDeviceIdAndNoToken() = runTest {
        val lines = mutableListOf<String>()
        val fake = FakeNintendo { json("""{"deviceId":"DEVICE-123","status":"TO_ADDED","nextStepDetail":{"estimatedBedtimeChanges":{"from":"19:00","to":"19:05"}}}""") }
        val api = ParentalApi(fake.client, TokenProvider { "SECRET-TOKEN" }, os = "ANDROID", logger = { lines += it })
        api.updateExtraPlayingTime("DEVICE-123", ExtraTimeStatus.TO_ADDED, 5)
        api.extraPlayingTimeState("DEVICE-123")
        assertEquals(3, lines.size, lines.toString())
        assertTrue(lines[0].startsWith("POST updateExtraPlayingTime TO_ADDED 5 min -> 200"), lines[0])
        assertEquals("  status TO_ADDED; next step: confirm (bedtime 19:00 -> 19:05)", lines[1])
        assertTrue(lines[2].startsWith("GET fetchExtraPlayingTimeState -> 200"), lines[2])
        assertTrue(lines.none { "DEVICE-123" in it || "SECRET-TOKEN" in it || "Bearer" in it }, lines.toString())
    }
}
