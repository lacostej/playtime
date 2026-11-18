package org.example.playtime.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Lenient on unknown fields (Nintendo adds fields often), strict on missing required ones. */
val NintendoJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun nintendoHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(NintendoJson) }
}

/** Turn anything thrown by [block] into an [ApiError]; cancellation passes through. */
internal suspend fun <T> mapErrors(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: ApiError) {
    throw e
} catch (e: SerializationException) {
    throw ApiError.ServiceChanged("unexpected response", e)
} catch (e: ContentConvertException) { // Ktor's wrapper around a body that doesn't match our model
    throw ApiError.ServiceChanged("unexpected response", e)
} catch (e: Exception) {
    throw ApiError.Network(e)
}
