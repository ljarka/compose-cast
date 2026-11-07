package io.composecast.server

import io.composecast.server.sdk.ScreenRegistry
import io.composecast.server.sdk.composeCast

val registry: ScreenRegistry = composeCast {
    brand(
        name = "ComposeCast Demo",
        primaryColor = "#6200EE",
        secondaryColor = "#03DAC6",
        backgroundColor = "#FAFAFA",
    )

    screen("main") {
        title = "Home"
        text("Welcome to ComposeCast", style = "h1")
        spacer(16)
        image("https://picsum.photos/400/200", alt = "Hero image")
        spacer(16)
        text("Server-driven UI for Compose", style = "body")
        button("Get started", action = "navigate:onboarding")
    }

    screen("onboarding") {
        title = "Get Started"
        text("How it works", style = "h2")
        text("The server defines your UI. The app renders it.", style = "body")
        button("Continue", action = "navigate:main")
    }
}
