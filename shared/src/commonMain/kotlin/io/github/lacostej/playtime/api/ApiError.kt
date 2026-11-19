package io.github.lacostej.playtime.api

/** Every way a call to Nintendo can fail, so the UI can show a clear message instead of crashing. */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The session token is no longer valid: the user has to log in again. */
    class LoginExpired(detail: String) : ApiError("Login expired: $detail")

    /** Nintendo changed something (headers rejected, unexpected response): update the app or use the official app. */
    class ServiceChanged(detail: String, cause: Throwable? = null) : ApiError("Nintendo's service changed: $detail", cause)

    /** Too many requests; wait before trying again. */
    class RateLimited(val retryAfterSeconds: Int?) : ApiError("Rate limited")

    /** Another change is still being applied (seen right after a cancel); retry shortly. */
    class Conflict : ApiError("Another change is in progress")

    class Network(cause: Throwable) : ApiError("Network error", cause)

    class Http(val status: Int, val errorCode: String?) : ApiError("HTTP $status ${errorCode.orEmpty()}")
}
