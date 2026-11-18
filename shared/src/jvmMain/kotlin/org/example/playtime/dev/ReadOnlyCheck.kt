package org.example.playtime.dev

import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.example.playtime.api.ParentalApi
import org.example.playtime.api.TokenProvider
import org.example.playtime.api.nintendoHttpClient
import org.example.playtime.auth.AccessToken
import org.example.playtime.auth.NintendoAuth
import org.example.playtime.planner.plan
import org.example.playtime.today.buildToday

/** Developer check: reads today's state with the shared client. Read-only; never prints the token or IDs. */
fun main() = runBlocking {
    val path = System.getenv("PLAYTIME_TOKEN_FILE") ?: error("Set PLAYTIME_TOKEN_FILE to a file holding a session token")
    val sessionToken = File(path).readText().trim()
    val http = nintendoHttpClient(OkHttp.create())
    val auth = NintendoAuth(http)
    var cached: AccessToken? = null
    val tokens = TokenProvider { force ->
        val c = cached
        if (!force && c != null && !c.expiresWithin(60, Clock.System.now())) c.idToken
        else auth.refresh(sessionToken).also { cached = it }.idToken
    }
    val api = ParentalApi(http, tokens, os = "ANDROID")
    try {
        val devices = api.ownedDevices().ownedDevices
        println("devices: ${devices.size}")
        for (d in devices) {
            val today = buildToday(api.parentalControlSetting(d.deviceId), api.dailySummaries(d.deviceId), d.device?.timeZone, Clock.System.now())
            println("label=${d.label} timeZone=${today.timeZone} beforeWakeUp=${today.beforeWakeUp} playedAsOf=${today.playedAsOf}")
            println("  ${today.state}")
            println("  untouched plan: ${plan(today.state, plan(today.state, -1).playableNow)}")
        }
    } finally {
        http.close()
    }
}
