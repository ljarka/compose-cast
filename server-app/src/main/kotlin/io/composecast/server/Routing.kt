package io.composecast.server

import io.composecast.protocol.Headers
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URLEncoder

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respondText("ok")
        }
        // Remote Compose binary document, rendered by the RC player in sdk-android.
        get("/screens/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val screen = registry.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Screen '$id' not found"))
            call.response.header(Headers.SCREEN_TITLE, URLEncoder.encode(screen.title, Charsets.UTF_8))
            call.respondBytes(screen.document, ContentType.Application.OctetStream)
        }
        // Human-readable dump of the decoded document, for debugging in a browser or curl.
        get("/screens/{id}/debug") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val screen = registry.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Screen '$id' not found"))
            call.respondText(
                "screen: ${screen.id}\ntitle: ${screen.title}\nsize: ${screen.size} bytes\n\n${screen.describe()}",
            )
        }
        get("/brand") {
            val brand = registry.brand
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "No brand configured"))
            call.respond(brand)
        }
        get("/screens") {
            call.respond(registry.ids())
        }
    }
}
