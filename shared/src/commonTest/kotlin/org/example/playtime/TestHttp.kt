package org.example.playtime

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Instant
import org.example.playtime.api.nintendoHttpClient

/** Records every request and answers from [handler]. */
class FakeNintendo(private val handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val requests = mutableListOf<HttpRequestData>()
    val client: HttpClient = nintendoHttpClient(MockEngine { request -> requests += request; handler(request) })
}

fun MockRequestHandleScope.json(body: String, status: Int = 200, vararg extraHeaders: Pair<String, String>) = respond(
    body, HttpStatusCode.fromValue(status),
    headersOf(*(listOf(HttpHeaders.ContentType to listOf("application/json")) + extraHeaders.map { it.first to listOf(it.second) }).toTypedArray()),
)

fun HttpRequestData.bodyText(): String = when (val b = body) {
    is TextContent -> b.text
    is FormDataContent -> b.formData.formUrlEncode()
    else -> ""
}

class FixedClock(private val epochSeconds: Long) : Clock {
    override fun now() = Instant.fromEpochSeconds(epochSeconds)
}
