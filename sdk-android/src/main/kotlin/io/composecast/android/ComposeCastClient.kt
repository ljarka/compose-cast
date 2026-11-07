package io.composecast.android

import io.composecast.protocol.Actions
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.Headers
import io.composecast.protocol.ScreenModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ComposeCastClient(private val config: ComposeCastConfig) {

    private val http = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        if (config.logEnabled) {
            install(Logging) { level = LogLevel.INFO }
        }
    }

    suspend fun getScreen(id: String): ScreenModel =
        http.get("${config.serverUrl}/screens/$id") {
            header(Headers.PLAYER_VERSION, config.playerVersion)
        }.body()

    suspend fun listScreens(): List<String> =
        http.get("${config.serverUrl}/screens").body()

    suspend fun getBrand(): BrandConfig =
        http.get("${config.serverUrl}/brand").body()

    fun close() = http.close()
}
