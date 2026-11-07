package io.composecast.android

import android.util.Log
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.Headers
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
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

    /**
     * Downloads a screen's Remote Compose document and decodes it off the main thread.
     * @throws ComposeCastHttpException when the server answers with a non-2xx status (e.g. 404);
     *   other exceptions (IOException, …) mean the server was not reached.
     */
    suspend fun getScreen(id: String): ScreenDocument {
        val response = try {
            http.get("${config.serverUrl}/screens/$id") {
                header(Headers.PLAYER_VERSION, config.playerVersion)
            }
        } catch (e: ResponseException) {
            throw ComposeCastHttpException(e.response.status.value, "HTTP ${e.response.status.value} for screen '$id'", e)
        }
        val title = response.headers[Headers.SCREEN_TITLE]
            ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
            ?: id
        val bytes = response.readRawBytes()
        return withContext(Dispatchers.Default) { ScreenDocument.fromBytes(id, title, bytes) }
    }

    suspend fun listScreens(): List<String> =
        http.get("${config.serverUrl}/screens").body()

    suspend fun getBrand(): BrandConfig =
        http.get("${config.serverUrl}/brand").body()

    fun close() = http.close()
}

/** The server answered with a non-2xx [status] (e.g. 404 = unknown screen). */
class ComposeCastHttpException(val status: Int, message: String, cause: Throwable? = null) : Exception(message, cause)
