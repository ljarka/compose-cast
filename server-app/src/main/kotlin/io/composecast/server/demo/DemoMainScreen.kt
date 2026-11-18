package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.server.sdk.RegistryBuilder

/** The demo `main` screen. */
internal fun RegistryBuilder.mainScreen() {
    screen("main") {
        title = "Home"
        text("Welcome to ComposeCast", style = "h1")
        spacer(16)
        image("https://picsum.photos/400/200", alt = "Hero image")
        spacer(16)
        text("Server-driven UI for Compose", style = "body")
        button("Get started", action = "navigate:onboarding")
        spacer(8)
        button("Live data", Action.Navigate("data"))
        spacer(8)
        button("Layouts", Action.Navigate("layouts"))
    }
}
