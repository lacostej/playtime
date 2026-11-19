package io.github.lacostej.playtime.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.parameters
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.lacostej.playtime.api.ApiError
import io.github.lacostej.playtime.api.mapErrors
import io.github.lacostej.playtime.config.NintendoApi

/** One login attempt: open [url] in a browser; keep [verifier] and [state] until the redirect comes back. */
data class LoginRequest(val url: String, val verifier: String, val state: String)

/** Short-lived token for the parental-controls API. */
data class AccessToken(val idToken: String, val expiresAt: Instant) {
    fun expiresWithin(seconds: Long, now: Instant) = expiresAt.epochSeconds - now.epochSeconds < seconds
}

@Serializable
private data class SessionTokenResponse(@SerialName("session_token") val sessionToken: String)

@Serializable
private data class TokenRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("grant_type") val grantType: String,
    @SerialName("session_token") val sessionToken: String,
)

@Serializable
private data class TokenResponse(
    @SerialName("id_token") val idToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

/** Nintendo Account login (OAuth with PKCE), done directly from the device. */
class NintendoAuth(private val http: HttpClient, private val clock: Clock = Clock.System) {

    fun startLogin(): LoginRequest {
        val verifier = randomLetters()
        val state = randomLetters()
        val query = parameters {
            append("client_id", NintendoApi.CLIENT_ID)
            append("redirect_uri", NintendoApi.REDIRECT_URI)
            append("response_type", "session_token_code")
            append("scope", NintendoApi.SCOPES.joinToString(" "))
            append("session_token_code_challenge", pkceChallenge(verifier))
            append("session_token_code_challenge_method", "S256")
            append("state", state)
            append("theme", "login_form")
        }.formUrlEncode()
        return LoginRequest("${NintendoApi.AUTHORIZE_URL}?$query", verifier, state)
    }

    /**
     * Extract the session-token code from the redirect link (npf…://auth#session_token_code=…&state=…),
     * whether it arrived through the app link or was pasted by the user.
     */
    fun sessionTokenCode(redirect: String, expectedState: String): String {
        val fragment = redirect.trim().substringAfter('#', missingDelimiterValue = "")
        val params = fragment.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
        }.toMap()
        require(params["state"] == expectedState) { "This link doesn't belong to the current login. Start again." }
        return requireNotNull(params["session_token_code"]) { "This isn't a login link." }
    }

    /** Exchange the one-time code for the long-lived session token (store it in the platform's secure storage). */
    suspend fun exchangeSessionToken(code: String, verifier: String): String = mapErrors {
        val response = http.submitForm(
            url = NintendoApi.SESSION_TOKEN_URL,
            formParameters = parameters {
                append("client_id", NintendoApi.CLIENT_ID)
                append("session_token_code", code)
                append("session_token_code_verifier", verifier)
            },
        )
        failOnLoginError(response)
        response.body<SessionTokenResponse>().sessionToken
    }

    /** Get a fresh API token from the session token (about once an hour). */
    suspend fun refresh(sessionToken: String): AccessToken = mapErrors {
        val response = http.post(NintendoApi.TOKEN_URL) {
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(NintendoApi.CLIENT_ID, NintendoApi.GRANT_TYPE, sessionToken))
        }
        failOnLoginError(response)
        val token = response.body<TokenResponse>()
        AccessToken(token.idToken, Instant.fromEpochSeconds(clock.now().epochSeconds + token.expiresIn))
    }

    private fun failOnLoginError(response: HttpResponse) {
        when (response.status.value) {
            200 -> Unit
            400 -> throw ApiError.LoginExpired("session rejected") // pynintendoauth: 400 = invalid session token
            401 -> throw ApiError.ServiceChanged("login configuration rejected")
            429 -> throw ApiError.RateLimited(response.headers["Retry-After"]?.toIntOrNull())
            else -> throw ApiError.Http(response.status.value, null)
        }
    }
}
