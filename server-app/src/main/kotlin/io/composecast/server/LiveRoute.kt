package io.composecast.server

import io.composecast.server.sdk.DataSourceException
import io.composecast.server.sdk.LiveFrames
import io.composecast.server.sdk.ScreenParameterException
import io.composecast.server.sdk.UnsupportedClientVersionException
import io.ktor.http.decodeURLPart
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/** WebSocket support for live screens; pings keep idle connections (no data change) open. */
internal fun Application.installLiveScreens() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }
}

/**
 * `ws(s)://…/screens/{id}/live?params` — live screens (T-047, [ADR 0029]): binary frames, each a
 * Remote Compose document. The first one is full; afterwards a frame is sent only when something
 * changed — a data-update document (`DOC_DATA_UPDATE`) when only data changed, the full document
 * when the structure changed. Same query and version headers as `GET /screens/{id}`. A screen
 * that is not live, unknown, missing a parameter or too new for the client closes the socket
 * with `CANNOT_ACCEPT` and the reason; the app keeps what `GET` gave it.
 */
internal fun Route.liveScreens(screens: LiveScreens) {
    webSocket("/screens/{id}/live") {
        // From the path: a screen param named `id` in the query must not win (as in GET, T-031).
        val id = call.request.path().removePrefix("/screens/").removeSuffix("/live").decodeURLPart()
        stream(id, LiveFrames(id, call.renderContext(id)) { screens.registryFor(id) })
    }
}

/** Sends [frames] until the client leaves or the screen is refused; logs open/close (one line each). */
private suspend fun WebSocketServerSession.stream(id: String, frames: LiveFrames) {
    val log = call.application.environment.log
    var sent = 0
    log.info("Live '{}' opened", id)
    try {
        while (isActive) {
            val frame = try {
                withContext(Dispatchers.IO) { frames.next() }
            } catch (e: NoSuchElementException) {
                return refuse(e.message ?: "screen not found")
            } catch (e: ScreenParameterException) {
                return refuse(e.message ?: "missing parameter")
            } catch (_: UnsupportedClientVersionException) {
                return refuse("app update required")
            } catch (_: DataSourceException) {
                // Already logged by the binder; keep the last frame on the device and try again.
                if (sent == 0) return refuse("data source failed")
                null
            }
            val everyMs = frames.everyMs ?: return refuse("screen is not live")
            if (frame != null) {
                send(Frame.Binary(true, frame.document))
                sent++
            }
            delay(everyMs.toLong())
        }
    } finally {
        log.info("Live '{}' closed after {} frames", id, sent)
    }
}

/** Close reasons are limited to 123 bytes by RFC 6455. */
private suspend fun WebSocketServerSession.refuse(reason: String) =
    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, reason.take(MAX_REASON)))

private const val MAX_REASON = 100
