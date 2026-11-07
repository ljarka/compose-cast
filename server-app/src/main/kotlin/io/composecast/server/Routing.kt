package io.composecast.server

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ComponentModel
import io.composecast.protocol.ScreenModel
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val sampleScreens = mapOf(
    "main" to ScreenModel(
        id = "main",
        title = "Home",
        components = listOf(
            ComponentModel.Text(text = "Welcome to ComposeCast", style = "h1"),
            ComponentModel.Spacer(height = 16),
            ComponentModel.Image(url = "https://picsum.photos/400/200", alt = "Hero image"),
            ComponentModel.Spacer(height = 16),
            ComponentModel.Text(text = "Server-driven UI for Compose", style = "body"),
            ComponentModel.Button(label = "Get started", action = "navigate:onboarding")
        )
    ),
    "onboarding" to ScreenModel(
        id = "onboarding",
        title = "Get Started",
        components = listOf(
            ComponentModel.Text(text = "How it works", style = "h2"),
            ComponentModel.Text(text = "The server defines your UI. The app renders it.", style = "body"),
            ComponentModel.Button(label = "Continue", action = "navigate:main")
        )
    )
)

private val brandConfig = BrandConfig(
    name = "ComposeCast Demo",
    primaryColor = "#6200EE",
    secondaryColor = "#03DAC6",
    backgroundColor = "#FAFAFA"
)

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respondText("ok")
        }
        get("/screens/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val screen = sampleScreens[id]
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Screen '$id' not found"))
            call.respond(screen)
        }
        get("/brand") {
            call.respond(brandConfig)
        }
    }
}
