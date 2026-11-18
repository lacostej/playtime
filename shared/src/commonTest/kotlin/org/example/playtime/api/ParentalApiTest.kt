package org.example.playtime.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.example.playtime.FakeNintendo
import org.example.playtime.bodyText
import org.example.playtime.config.NintendoApi
import org.example.playtime.json

class ParentalApiTest {
    private val refreshes = mutableListOf<Boolean>()
    private val tokens = TokenProvider { force -> refreshes += force; if (force) "NEW" else "OLD" }

    private fun api(fake: FakeNintendo) = ParentalApi(fake.client, tokens, os = "ANDROID", timeZone = "Europe/Paris")

    @Test
    fun sendsHonestIdentityAndTheRequiredHeaders() = runTest {
        val fake = FakeNintendo { json("""{"ownedDevices":[]}""") }
        api(fake).ownedDevices()
        val h = fake.requests.single().headers
        assertEquals("Bearer OLD", h["Authorization"])
        assertEquals(NintendoApi.USER_AGENT, h["User-Agent"])
        assertEquals(NintendoApi.APP_ID, h["X-Moon-App-Id"])
        assertEquals("ANDROID", h["X-Moon-Os"])
        assertEquals(NintendoApi.APP_INTERNAL_VERSION, h["X-Moon-App-Internal-Version"])
        assertEquals("Europe/Paris", h["X-Moon-TimeZone"])
        assertNull(h["X-Moon-Model"]) // nothing beyond what the API requires
    }

    @Test
    fun expiredTokenIsRefreshedOnceThenRetried() = runTest {
        val fake = FakeNintendo { req -> if (req.headers["Authorization"] == "Bearer OLD") json("{}", 401) else json("""{"ownedDevices":[]}""") }
        api(fake).ownedDevices()
        assertEquals(listOf(false, true), refreshes)
        assertEquals(2, fake.requests.size)
    }

    @Test
    fun tokenRejectedTwiceMeansLoginAgain() = runTest {
        val fake = FakeNintendo { json("{}", 401) }
        assertFailsWith<ApiError.LoginExpired> { api(fake).ownedDevices() }
        assertEquals(2, fake.requests.size) // no retry loop
    }

    @Test
    fun rejectedHeadersMeanTheServiceChanged() = runTest {
        val fake = FakeNintendo { json("""{"type":"x","status":400,"errorCode":"invalid_headers","title":"Invalid headers"}""", 400) }
        assertFailsWith<ApiError.ServiceChanged> { api(fake).ownedDevices() }
    }

    @Test
    fun unexpectedResponseShapeMeansTheServiceChanged() = runTest {
        val fake = FakeNintendo { json("""{"parentalControlSetting":{"somethingElse":1}}""") }
        assertFailsWith<ApiError.ServiceChanged> { api(fake).parentalControlSetting("dev") }
    }

    @Test
    fun conflictAndRateLimitAreTyped() = runTest {
        assertFailsWith<ApiError.Conflict> { api(FakeNintendo { json("{}", 409) }).extraPlayingTimeState("dev") }
        val limited = assertFailsWith<ApiError.RateLimited> { api(FakeNintendo { json("{}", 429, "Retry-After" to "30") }).ownedDevices() }
        assertEquals(30, limited.retryAfterSeconds)
    }

    @Test
    fun updateSendsMinutesAndExposesTheNextStep() = runTest {
        val fake = FakeNintendo { json("""{"deviceId":"dev","status":"TO_ADDED","nextStepDetail":{"estimatedBedtimeChanges":{"from":"19:00","to":"19:15"}},"updatedAt":1}""") }
        val r = api(fake).updateExtraPlayingTime("dev", ExtraTimeStatus.TO_ADDED, 15)
        assertEquals("19:15", assertNotNull(r.nextStepDetail).estimatedBedtimeChanges?.to)
        assertEquals("""{"deviceId":"dev","status":"TO_ADDED","additionalTime":15}""", fake.requests.single().bodyText())
    }

    @Test
    fun cancelSendsNoMinutesAndHasNoNextStep() = runTest {
        val fake = FakeNintendo { json("""{"deviceId":"dev","status":"TO_CANCELED","nextStepDetail":null,"updatedAt":1}""") }
        val r = api(fake).updateExtraPlayingTime("dev", ExtraTimeStatus.TO_CANCELED)
        assertNull(r.nextStepDetail)
        assertEquals("""{"deviceId":"dev","status":"TO_CANCELED"}""", fake.requests.single().bodyText())
    }

    @Test
    fun confirmSendsTheBedtimeChoice() = runTest {
        val fake = FakeNintendo { json("""{"deviceId":"dev","status":"TO_ADDED","updatedAt":1}""") }
        api(fake).confirmExtraPlayingTime("dev", 5, withBedtime = false)
        assertContains(fake.requests.single().bodyText(), "\"withBedtime\":false")
        assertContains(fake.requests.single().url.toString(), "/v2/actions/device/confirmExtraPlayingTime")
    }
}
