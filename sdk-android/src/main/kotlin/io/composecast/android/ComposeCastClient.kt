package io.composecast.android

import android.util.Log
import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.Headers
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URLDecoder

class ComposeCastClient(private val config: ComposeCastConfig) {

    private val http = HttpClient(Android) {
        // Non-2xx responses throw (e.g. 404 for an unknown screen) instead of failing to decode.
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        if (config.logEnabled) {
            install(Logging) {
                level = LogLevel.INFO
                // Ktor's default logger goes through SLF4J, which has no binding on Android:
                // without this, logEnabled silently printed nothing.
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("ComposeCast", message)
                    }
                }
            }
        }
    }

    /** Last document per (screen, params), revalidated with `If-None-Match`. Bounded LRU. */
    private val cache = object : LinkedHashMap<String, CachedScreen>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedScreen>) = size > CACHE_SIZE
    }

    private class CachedScreen(val etag: String, val title: String, val bytes: ByteArray)

    /**
     * Downloads a screen's Remote Compose document (`GET /screens/{id}?params`, URL-encoded) and
     * decodes it off the main thread. A cached copy is revalidated with its ETag; on `304` the
     * cached bytes are decoded again (a fresh document: played documents keep runtime state).
     * @throws ComposeCastHttpException when the server answers with a non-2xx status (e.g. 404,
     *   400 for a missing parameter); other exceptions (IOException, …) mean it was not reached.
     */
    suspend fun getScreen(id: String, params: Map<String, String> = emptyMap()): ScreenDocument {
        val key = ActionCodec.encode(Action.Navigate(id, params))
        val cached = synchronized(cache) { cache[key] }
        val response = http.get(config.serverUrl) {
            url {
                appendPathSegments("screens", id)
                params.forEach { (name, value) -> parameters.append(name, value) }
            }
            header(Headers.PLAYER_VERSION, config.playerVersion)
            cached?.let { header(HttpHeaders.IfNoneMatch, it.etag) }
            expectSuccess = false // 304 is not an error here
        }
        val screen = when {
            response.status == HttpStatusCode.NotModified && cached != null -> cached
            !response.status.isSuccess() -> throw ComposeCastHttpException(
                response.status.value,
                "HTTP ${response.status.value} for screen '$id'",
            )
            else -> {
                val title = response.headers[Headers.SCREEN_TITLE]
                    ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
                    ?: id
                CachedScreen(response.headers[HttpHeaders.ETag].orEmpty(), title, response.readRawBytes())
                    .also { fresh -> if (fresh.etag.isNotEmpty()) synchronized(cache) { cache[key] = fresh } }
            }
        }
        return withContext(Dispatchers.Default) { ScreenDocument.fromBytes(id, screen.title, screen.bytes) }
    }

    suspend fun listScreens(): List<String> = http.get("${config.serverUrl}/screens").body()

    suspend fun getBrand(): BrandConfig = http.get("${config.serverUrl}/brand").body()

    fun close() = http.close()

    private companion object {
        const val CACHE_SIZE = 32
    }
}

/** The server answered with a non-2xx [status] (e.g. 404 = unknown screen). */
class ComposeCastHttpException(val status: Int, message: String, cause: Throwable? = null) : Exception(message, cause)
