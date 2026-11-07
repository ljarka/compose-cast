package io.composecast.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respondText("ok")
        }
        get("/screens/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val screen = registry.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Screen '$id' not found"))
            call.respond(screen)
        }
        get("/brand") {
            val brand = registry.brand
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "No brand configured"))
            call.respond(brand)
        }
        get("/screens") {
            call.respond(registry.all().keys.toList())
        }
    }
}
