package io.github.lacostej.playtime.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.github.lacostej.playtime.config.NintendoApi

/** Receives one line per notable event. Never pass tokens, IDs or names to it. */
fun interface ActivityLogger {
    fun log(message: String)

    companion object {
        val None = ActivityLogger { }
    }
}

/** Supplies the API token; [forceRefresh] after the API rejected the current one. */
fun interface TokenProvider {
    suspend fun idToken(forceRefresh: Boolean): String
}

/**
 * The only place that talks to the parental-controls API.
 * [os] is the real platform ("ANDROID" or "IOS"); [timeZone] is the phone's IANA zone, if known.
 */
class ParentalApi(
    private val http: HttpClient,
    private val tokens: TokenProvider,
    private val os: String,
    private val timeZone: String? = null,
    private val logger: ActivityLogger = ActivityLogger.None,
) {
    suspend fun ownedDevices(): OwnedDevicesResponse = get("/v2/actions/user/fetchOwnedDevices")

    suspend fun parentalControlSetting(deviceId: String): ParentalControlSettingResponse =
        get("/v2/actions/parentalControlSetting/fetchParentalControlSetting?deviceId=$deviceId")

    suspend fun extraPlayingTimeState(deviceId: String): ExtraPlayingTimeState =
        get("/v2/actions/device/fetchExtraPlayingTimeState?deviceId=$deviceId")

    suspend fun dailySummaries(deviceId: String): DailySummariesResponse =
        get("/v2/actions/playSummary/fetchDailySummaries?deviceId=$deviceId")

    /**
     * Add ([ExtraTimeStatus.TO_ADDED] with [minutes]) or cancel ([ExtraTimeStatus.TO_CANCELED]).
     * Observed: when the response has a nextStepDetail, nothing is applied until [confirmExtraPlayingTime];
     * without one (e.g. after bedtime, or a cancel) the update itself applies.
     */
    suspend fun updateExtraPlayingTime(deviceId: String, status: ExtraTimeStatus, minutes: Int? = null): UpdateExtraPlayingTimeResponse {
        val r: UpdateExtraPlayingTimeResponse =
            post("/v2/actions/device/updateExtraPlayingTime", UpdateExtraPlayingTimeRequest(deviceId, status.name, minutes))
        val next = r.nextStepDetail?.estimatedBedtimeChanges
        logger.log("  status ${r.status}; " + if (r.nextStepDetail != null) "next step: confirm (bedtime ${next?.from} -> ${next?.to})" else "no next step: applied by the update")
        refuseOn(r.status)
        return r
    }

    /** Apply a pending add; [withBedtime] moves bedtime by the same minutes. Only call after an update that returned a nextStepDetail. */
    suspend fun confirmExtraPlayingTime(deviceId: String, minutes: Int, withBedtime: Boolean): ConfirmExtraPlayingTimeResponse {
        val r: ConfirmExtraPlayingTimeResponse =
            post("/v2/actions/device/confirmExtraPlayingTime", ConfirmExtraPlayingTimeRequest(deviceId, minutes, withBedtime))
        logger.log("  status ${r.status}")
        refuseOn(r.status)
        return r
    }

    private fun refuseOn(status: String?) {
        if (status in REFUSED_STATUSES) throw ApiError.Refused(status!!)
    }

    private suspend inline fun <reified T> get(path: String): T = call("GET ${endpoint(path)}") { http.get(NintendoApi.API_BASE + path) { headers(it) } }

    private suspend inline fun <reified T> post(path: String, body: Any): T = call("POST ${endpoint(path)} ${describe(body)}") {
        http.post(NintendoApi.API_BASE + path) {
            headers(it)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** Endpoint name only: no host, no query (the query holds the device ID). */
    private fun endpoint(path: String) = path.substringBefore('?').substringAfterLast('/')

    /** Request body without the device ID. */
    private fun describe(body: Any) = when (body) {
        is UpdateExtraPlayingTimeRequest -> body.status + (body.additionalTime?.let { " $it min" } ?: "")
        is ConfirmExtraPlayingTimeRequest -> "${body.additionalTime} min withBedtime=${body.withBedtime}"
        else -> ""
    }

    private fun HttpRequestBuilder.headers(idToken: String) {
        header(HttpHeaders.Authorization, "Bearer $idToken")
        header(HttpHeaders.UserAgent, NintendoApi.USER_AGENT)
        header("X-Moon-App-Id", NintendoApi.APP_ID)
        header("X-Moon-Os", os)
        header("X-Moon-App-Internal-Version", NintendoApi.APP_INTERNAL_VERSION)
        if (timeZone != null) header("X-Moon-TimeZone", timeZone)
    }

    /** Send once; on 401 refresh the token and retry exactly once. */
    private suspend inline fun <reified T> call(label: String, crossinline send: suspend (String) -> HttpResponse): T = mapErrors {
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        var response = send(tokens.idToken(forceRefresh = false))
        if (response.status.value == 401) {
            logger.log("$label -> 401, refreshing the token")
            response = send(tokens.idToken(forceRefresh = true))
        }
        logger.log("$label -> ${response.status.value} (${started.elapsedNow().inWholeMilliseconds} ms)")
        when (val status = response.status.value) {
            200 -> if (T::class == Unit::class) Unit as T else response.body<T>()
            401, 403 -> throw ApiError.LoginExpired("API rejected the token")
            409 -> throw ApiError.Conflict()
            429 -> throw ApiError.RateLimited(response.headers[HttpHeaders.RetryAfter]?.toIntOrNull())
            else -> {
                val code = runCatching { NintendoJson.decodeFromString(ErrorBody.serializer(), response.bodyAsText()).errorCode }.getOrNull()
                if (status == 400 && code == "invalid_headers") throw ApiError.ServiceChanged("request headers rejected")
                if (code == "update_required") throw ApiError.ServiceChanged("app update required") // as handled by Home Assistant's integration
                logger.log("  error code: ${code ?: "none"}")
                throw ApiError.Http(status, code)
            }
        }
    }
}
