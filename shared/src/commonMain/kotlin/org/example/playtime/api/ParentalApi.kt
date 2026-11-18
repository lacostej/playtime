package org.example.playtime.api

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
import org.example.playtime.config.NintendoApi

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
    suspend fun updateExtraPlayingTime(deviceId: String, status: ExtraTimeStatus, minutes: Int? = null): UpdateExtraPlayingTimeResponse =
        post("/v2/actions/device/updateExtraPlayingTime", UpdateExtraPlayingTimeRequest(deviceId, status.name, minutes))

    /** Apply a pending add; [withBedtime] moves bedtime by the same minutes. Only call after an update that returned a nextStepDetail. */
    suspend fun confirmExtraPlayingTime(deviceId: String, minutes: Int, withBedtime: Boolean) {
        post<Unit>("/v2/actions/device/confirmExtraPlayingTime", ConfirmExtraPlayingTimeRequest(deviceId, minutes, withBedtime))
    }

    private suspend inline fun <reified T> get(path: String): T = call { http.get(NintendoApi.API_BASE + path) { headers(it) } }

    private suspend inline fun <reified T> post(path: String, body: Any): T = call {
        http.post(NintendoApi.API_BASE + path) {
            headers(it)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
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
    private suspend inline fun <reified T> call(crossinline send: suspend (String) -> HttpResponse): T = mapErrors {
        var response = send(tokens.idToken(forceRefresh = false))
        if (response.status.value == 401) response = send(tokens.idToken(forceRefresh = true))
        when (val status = response.status.value) {
            200 -> if (T::class == Unit::class) Unit as T else response.body<T>()
            401, 403 -> throw ApiError.LoginExpired("API rejected the token")
            409 -> throw ApiError.Conflict()
            429 -> throw ApiError.RateLimited(response.headers[HttpHeaders.RetryAfter]?.toIntOrNull())
            else -> {
                val code = runCatching { NintendoJson.decodeFromString(ErrorBody.serializer(), response.bodyAsText()).errorCode }.getOrNull()
                if (status == 400 && code == "invalid_headers") throw ApiError.ServiceChanged("request headers rejected")
                throw ApiError.Http(status, code)
            }
        }
    }
}
