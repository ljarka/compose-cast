package io.composecast.android

import android.util.Log
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcProfiles
import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.Headers
import io.composecast.protocol.UpgradeRequiredResponse
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URLDecoder

class ComposeCastClient internal constructor(
    private val config: ComposeCastConfig,
    engine: HttpClientEngine?,
    private val decode: (id: String, title: String, bytes: ByteArray, liveEveryMs: Int?) -> ScreenDocument =
        ScreenDocument::fromBytes,
    private val cacheSize: Int = CACHE_SIZE,
) {
    constructor(config: ComposeCastConfig) : this(config, engine = null)

    private val http = if (engine != null) {
        HttpClient(engine) { configureHttp() }
    } else {
        HttpClient(OkHttp) { configureHttp() }
    }

    private fun HttpClientConfig<*>.configureHttp() {
        // Non-2xx responses throw (e.g. 404 for an unknown screen) instead of failing to decode.
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMs
            requestTimeoutMillis = config.requestTimeoutMs
            socketTimeoutMillis = config.socketTimeoutMs
        }
        install(WebSockets)
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
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedScreen>) = size > cacheSize
    }

    private class CachedScreen(val etag: String, val title: String, val bytes: ByteArray, val liveEveryMs: Int?)

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
            header(Headers.RC_API_LEVEL, CoreDocument.DOCUMENT_API_LEVEL)
            header(Headers.RC_PROFILES, RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL)
            cached?.let { header(HttpHeaders.IfNoneMatch, it.etag) }
            expectSuccess = false // 304 is not an error here
        }
        val screen = when {
            response.status == HttpStatusCode.NotModified && cached != null -> cached
            response.status == HttpStatusCode.UpgradeRequired -> {
                val upgrade = runCatching { response.body<UpgradeRequiredResponse>() }.getOrNull()
                throw ComposeCastUpgradeRequiredException(
                    minApiLevel = upgrade?.minApiLevel ?: (CoreDocument.DOCUMENT_API_LEVEL + 1),
                    message = upgrade?.error ?: "App update required",
                )
            }
            !response.status.isSuccess() -> throw ComposeCastHttpException(
                response.status.value,
                "HTTP ${response.status.value} for screen '$id'",
            )
            else -> {
                val title = response.headers[Headers.SCREEN_TITLE]
                    ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
                    ?: id
                CachedScreen(
                    response.headers[HttpHeaders.ETag].orEmpty(),
                    title,
                    response.readRawBytes(),
                    response.headers[Headers.LIVE]?.toIntOrNull(),
                )
                    .also { fresh -> if (fresh.etag.isNotEmpty()) synchronized(cache) { cache[key] = fresh } }
            }
        }
        return withContext(Dispatchers.Default) {
            decode(id, screen.title, screen.bytes, screen.liveEveryMs)
        }
    }

    /**
     * Updates of a live screen (T-047, ADR 0029; [ScreenDocument.liveEveryMs] is set): opens
     * `/screens/{id}/live` (WebSocket, same query and version headers as [getScreen]) and emits
     * every document the server pushes — full documents and data updates; pass the flow to
     * [ComposeCastScreen], which applies both. Reconnects with backoff (1 s … 30 s) while
     * collected; completes when the server refuses the screen (not live, unknown). Collect it
     * only while the screen is visible: [ComposeCastScreen] does that.
     */
    fun liveScreen(id: String, params: Map<String, String> = emptyMap()): Flow<ScreenDocument> = channelFlow {
        var attempt = 0
        while (true) {
            var refused = false
            try {
                http.webSocket(
                    request = {
                        url(config.serverUrl.replaceFirst("http", "ws"))
                        url {
                            appendPathSegments("screens", id, "live")
                            params.forEach { (name, value) -> parameters.append(name, value) }
                        }
                        timeout {
                            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        }
                        header(Headers.PLAYER_VERSION, config.playerVersion)
                        header(Headers.RC_API_LEVEL, CoreDocument.DOCUMENT_API_LEVEL)
                        header(Headers.RC_PROFILES, RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL)
                    },
                ) {
                    attempt = 0
                    for (frame in incoming) {
                        if (frame !is Frame.Binary) continue
                        val bytes = frame.readBytes()
                        send(withContext(Dispatchers.Default) { ScreenDocument.fromBytes(id, id, bytes) })
                    }
                    val reason = closeReason.await()
                    refused = reason?.code == CloseReason.Codes.CANNOT_ACCEPT.code
                    if (config.logEnabled) Log.d("ComposeCast", "live '$id' closed: $reason")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (config.logEnabled) Log.d("ComposeCast", "live '$id' failed: ${e.message}")
            }
            if (refused) return@channelFlow
            delay((LIVE_RETRY_MS shl attempt.coerceAtMost(LIVE_MAX_SHIFT)).coerceAtMost(LIVE_MAX_RETRY_MS))
            attempt++
        }
    }

    suspend fun listScreens(): List<String> = http.get("${config.serverUrl}/screens").body()

    suspend fun getBrand(): BrandConfig = http.get("${config.serverUrl}/brand").body()

    fun close() {
        http.close()
    }

    private companion object {
        const val CACHE_SIZE = 32
        const val LIVE_RETRY_MS = 1000L
        const val LIVE_MAX_SHIFT = 5
        const val LIVE_MAX_RETRY_MS = 30_000L
    }
}

/** The server answered with a non-2xx [status] (e.g. 404 = unknown screen). */
open class ComposeCastHttpException(val status: Int, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** The server answered 426 Upgrade Required: this app's Remote Compose player is too old (T-004). */
class ComposeCastUpgradeRequiredException(val minApiLevel: Int, message: String) :
    ComposeCastHttpException(426, message)
