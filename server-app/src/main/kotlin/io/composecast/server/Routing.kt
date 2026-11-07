package io.composecast.server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respondText("ok")
        }
        get("/screens/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(404)
            call.respondText("screen: $id (not implemented yet)", status = io.ktor.http.HttpStatusCode.NotImplemented)
        }
        get("/brand") {
            call.respondText("brand (not implemented yet)", status = io.ktor.http.HttpStatusCode.NotImplemented)
        }
    }
}
