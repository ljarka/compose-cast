package io.composecast.server

import io.composecast.server.sdk.ScreenParameterException
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
 */
fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    // Built before the server starts: an invalid screen stops startup with a clear message.
    val registry = loadScreens(System.getenv("COMPOSECAST_DATA_DIR")?.let(Path::of))
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(LiveScreens(registry)) }.start(wait = true)
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
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error for ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "internal error")))
        }
    }
    configureRouting(screens)
}
