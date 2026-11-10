package io.composecast.server

import io.composecast.protocol.ErrorResponse
import io.composecast.protocol.Headers
import io.composecast.server.sdk.RemoteScreen
import io.composecast.server.sdk.RenderContext
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

fun Application.configureRouting(screens: LiveScreens) {
    routing {
        get("/health") {
            call.respondText("ok")
        }
        // Remote Compose binary document, rendered by the RC player in sdk-android.
        // Query parameters are the screen's RenderContext.params (dynamic screens use them).
        get("/screens/{id}") {
            val screen = call.renderScreen(screens) ?: return@get
            val etag = "\"${screen.etag}\""
            call.response.header(HttpHeaders.ETag, etag)
            // Clients may keep the document but must revalidate (cheap 304) before using it.
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            if (etagMatches(call.request.headers[HttpHeaders.IfNoneMatch], etag)) {
                return@get call.respond(HttpStatusCode.NotModified)
            }
            call.response.header(Headers.SCREEN_TITLE, URLEncoder.encode(screen.title, Charsets.UTF_8))
            call.respondBytes(screen.document, ContentType.Application.OctetStream)
        }
        // Human-readable dump of the decoded document, for debugging in a browser or curl.
        get("/screens/{id}/debug") {
            val screen = call.renderScreen(screens) ?: return@get
            call.respondText(
                "screen: ${screen.id}\ntitle: ${screen.title}\nsize: ${screen.size} bytes\n" +
                    "etag: ${screen.etag}\n\n${screen.describe()}",
            )
        }
        get("/brand") {
            val brand = screens.current.brand
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("brand_not_configured", "No brand configured"),
                )
            call.respond(brand)
        }
        get("/screens") {
            call.respond(screens.current.ids())
        }
    }
}

/** Renders `{id}` with the query parameters; responds 404 itself and returns null when unknown. */
private suspend fun ApplicationCall.renderScreen(screens: LiveScreens): RemoteScreen? {
    val id = parameters["id"]!!
    val params = request.queryParameters.entries().associate { (name, values) -> name to values.first() }
    // Bound screens block on data sources (T-006): keep them off the request threads.
    val registry = screens.current
    val screen = withContext(Dispatchers.IO) { registry.render(id, RenderContext(params)) }
    if (screen == null) respond(HttpStatusCode.NotFound, ErrorResponse("screen_not_found", "Screen '$id' not found"))
    return screen
}

/** `If-None-Match` may list several tags, weak (`W/`) ones or `*` (RFC 9110 §13.1.2). */
internal fun etagMatches(ifNoneMatch: String?, etag: String): Boolean = ifNoneMatch != null &&
    ifNoneMatch.split(',').map { it.trim().removePrefix("W/") }.any { it == "*" || it == etag }
