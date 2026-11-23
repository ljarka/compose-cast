package io.composecast.server

import io.composecast.protocol.UpgradeRequiredResponse
import io.composecast.server.sdk.DataSourceException
import io.composecast.server.sdk.HttpImageLoader
import io.composecast.server.sdk.ScreenParameterException
import io.composecast.server.sdk.UnsupportedClientVersionException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import java.nio.file.Path

/**
 * Configuration (environment variables):
 * - `PORT` (default 8080)
 * - `COMPOSECAST_DATA_DIR`: serve `screens/<id>.json` (+ optional `brand.json`) from this directory
 *   instead of the built-in demo screens (layout: ADR 0011).
 * - `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS=true`: allow image URLs on loopback/private/link-local
 *   hosts (local development only; default false, T-026).
 * - `COMPOSECAST_RELOAD=false`: do not reload the data directory when its files change (default:
 *   reload, polled every 2 s; T-042).
 */
fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    // Built before the server starts: an invalid screen stops startup with a clear message.
    val allowPrivateImageHosts = System.getenv("COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS").toBoolean()
    val imageLoader = HttpImageLoader(allowPrivateHosts = allowPrivateImageHosts)
    val dataDir = System.getenv("COMPOSECAST_DATA_DIR")?.let(Path::of)
    val screens = LiveScreens(loadScreens(dataDir, imageLoader = imageLoader))
    if (dataDir != null && System.getenv("COMPOSECAST_RELOAD")?.toBoolean() != false) {
        DataDirReloader(dataDir, screens, load = { loadScreens(it, imageLoader = imageLoader) }).start()
    }
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(screens) }.start(wait = true)
}

fun Application.module(screens: LiveScreens) {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true })
    }
    install(StatusPages) {
        // A dynamic screen without a required query parameter: the client's fault.
        exception<ScreenParameterException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
        }
        // An external API failed and the screen has no `error` components (T-006); already logged.
        exception<DataSourceException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to cause.message))
        }
        // Client player RC API level is too old for the requested screen (T-004).
        exception<UnsupportedClientVersionException> { call, cause ->
            call.respond(
                HttpStatusCode.UpgradeRequired,
                UpgradeRequiredResponse(cause.message ?: "App update required", cause.minApiLevel),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error for ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "internal error")))
        }
    }
    configureRouting(screens)
}
