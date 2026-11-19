package io.github.lacostej.playtime.apply

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.lacostej.playtime.FakeNintendo
import io.github.lacostej.playtime.bodyText
import io.github.lacostej.playtime.json
import io.github.lacostej.playtime.planner.LAST_MINUTE

/**
 * Nintendo's extra-time endpoints as observed in 2026-11 (synthetic data):
 * - add before bedtime: update returns a nextStepDetail and changes nothing; confirm applies.
 * - add after bedtime: update applies directly (bedtime moves by the minutes), no nextStepDetail.
 * - confirm always applies, even without a pending update.
 * - changes show after [lagReads] setting reads; the applied total shows one read later still.
 */
class FakeConsole(
    val now: Int,
    val configuredBedtime: Int,
    extension: Int? = null,
    extendedBedtime: Int? = null,
    /** If set, the first bedtime move starts from this stale value instead of the setting (seen on 2026-11-18). */
    private val staleBase: Int? = null,
    private val lagReads: Int = 1,
    private var conflictsAfterCancel: Int = 0,
    private val ignoreAdds: Boolean = false,
    /** An add of the same minutes as the previous one replaces the extension instead of adding (seen once on 2026-11-19). */
    private val repeatReplaces: Boolean = false,
    /** If set, every confirm answers 200 with this status and changes nothing. */
    private val refuseWith: String? = null,
) {
    var extension: Int? = extension; private set
    var extendedBedtime: Int? = extendedBedtime; private set
    var updates = 0; private set
    var confirms = 0; private set

    private var shownExtension = extension
    private var shownBedtime = extendedBedtime
    private var shownApplied = extension
    private var readsSinceChange = Int.MAX_VALUE
    private var pendingConflicts = 0

    val http = FakeNintendo { handle(it) }

    private fun changed() { readsSinceChange = 0 }

    private fun base() = extendedBedtime ?: staleBase ?: configuredBedtime

    private var lastAdd: Int? = null

    private fun add(minutes: Int, moveBedtime: Boolean) {
        if (moveBedtime) extendedBedtime = minOf(base() + minutes, LAST_MINUTE)
        extension = if (repeatReplaces && minutes == lastAdd) minutes else (extension ?: 0) + minutes
        lastAdd = minutes
        changed()
    }

    private fun hm(m: Int) = "\"" + (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0') + "\""

    private fun MockRequestHandleScope.handle(r: HttpRequestData) = when {
        r.method == HttpMethod.Get && r.url.encodedPath.endsWith("fetchParentalControlSetting") -> {
            readsSinceChange++
            if (readsSinceChange >= lagReads) { shownExtension = extension; shownBedtime = extendedBedtime }
            val extra = shownExtension?.let { d ->
                val bed = shownBedtime?.let { """{"endTime":{"hour":${it / 60},"minute":${it % 60}}}""" } ?: "null"
                """{"inOneDay":{"duration":$d,"isInfinity":false},"bedtime":$bed,"expiresAt":null}"""
            } ?: "null"
            json("""{"parentalControlSetting":{"playTimerRegulations":{"timerMode":"DAILY","dailyRegulations":{}}},"ownedDevice":{"device":{"extraPlayingTime":$extra}}}""")
        }
        r.method == HttpMethod.Get && r.url.encodedPath.endsWith("fetchExtraPlayingTimeState") -> {
            // The applied total lags one read behind the setting and is empty while a change is in flight.
            shownApplied = if (readsSinceChange >= lagReads + 1) extension else if (readsSinceChange >= lagReads) null else shownApplied
            json("""{"deviceId":"dev","status":"SUCCESS","extendedPlayingTime":${shownApplied ?: "null"}}""")
        }
        r.url.encodedPath.endsWith("updateExtraPlayingTime") -> {
            updates++
            val body = Json.parseToJsonElement(r.bodyText()).jsonObject
            if (body["status"]!!.jsonPrimitive.content == "TO_CANCELED") {
                extension = null; extendedBedtime = null; lastAdd = null; changed()
                pendingConflicts = conflictsAfterCancel; conflictsAfterCancel = 0
                json("""{"deviceId":"dev","status":"TO_CANCELED","nextStepDetail":null}""")
            } else if (pendingConflicts > 0) {
                pendingConflicts--
                json("""{"errorCode":"conflict"}""", 409)
            } else {
                val minutes = body["additionalTime"]!!.jsonPrimitive.int
                val b = base()
                if (now < b) {
                    json("""{"deviceId":"dev","status":"TO_ADDED","nextStepDetail":{"estimatedBedtimeChanges":{"from":${hm(b)},"to":${hm(minOf(b + minutes, LAST_MINUTE))}}}}""")
                } else {
                    if (!ignoreAdds) add(minutes, moveBedtime = true)
                    json("""{"deviceId":"dev","status":"TO_ADDED","nextStepDetail":null}""")
                }
            }
        }
        r.url.encodedPath.endsWith("confirmExtraPlayingTime") -> {
            confirms++
            val body = Json.parseToJsonElement(r.bodyText()).jsonObject
            if (refuseWith != null) {
                json("""{"deviceId":"dev","status":"$refuseWith"}""")
            } else {
                if (!ignoreAdds) add(body["additionalTime"]!!.jsonPrimitive.int, body["withBedtime"]!!.jsonPrimitive.boolean)
                json("""{"deviceId":"dev","status":"TO_ADDED"}""")
            }
        }
        else -> json("{}", 404)
    }
}
