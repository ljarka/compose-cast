package io.composecast.server

import io.composecast.protocol.Action
import io.composecast.protocol.BrandConfig
import io.composecast.server.sdk.HttpImageLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.ScreenRegistry
import io.composecast.server.sdk.composeCast

/** Screens served by this server. Rendered on first use (fetches images over the network). */
val registry: ScreenRegistry by lazy { demoScreens() }

val demoBrand = BrandConfig(
    name = "ComposeCast Demo",
    primaryColor = "#6200EE",
    secondaryColor = "#03DAC6",
    backgroundColor = "#FAFAFA",
)

/**
 * The demo screens (a JSON copy in `src/test/resources/screens/` must render identically —
 * `JsonScreensTest`; update both). A function so that screenshot tests (`compat-tests`) render exactly these
 * screens with a fake [ImageLoader], without network access.
 */
fun demoScreens(imageLoader: ImageLoader = HttpImageLoader): ScreenRegistry = composeCast(imageLoader) {
    brand(demoBrand)

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
        spacer(8)
        button("Actions demo", Action.Navigate("actions"))
    }

    // One button per action type, for checking action handling on a device.
    screen("actions") {
        title = "Actions"
        text("Every action type", style = "h2")
        button("Open URL", Action.OpenUrl("https://example.com"))
        spacer(8)
        button("Reload", Action.Reload)
        spacer(8)
        button("Track event", Action.Track("demo_tap", mapOf("source" to "actions")))
        spacer(8)
        button("Custom", Action.Custom("hello"))
        spacer(8)
        button("Back to home", Action.Navigate("main"))
    }
}
