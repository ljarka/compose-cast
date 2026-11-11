package io.composecast.server

import io.composecast.protocol.Action
import io.composecast.protocol.BrandConfig
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.HttpDataLoader
import io.composecast.server.sdk.HttpImageLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.ScreenRegistry
import io.composecast.server.sdk.composeCast
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * The screens this server serves: from [dataDir] (`screens/<id>.json`, optional `brand.json` with a
 * `BrandConfig`; ADR 0011 layout) or, when it is null, the built-in [demoScreens].
 * Renders everything (fetching images), so call it once at startup.
 */
fun loadScreens(
    dataDir: Path?,
    imageLoader: ImageLoader = HttpImageLoader,
    dataLoader: DataLoader = HttpDataLoader,
): ScreenRegistry {
    if (dataDir == null) return demoScreens(imageLoader, dataLoader)
    val brandFile = dataDir.resolve("brand.json")
    return composeCast(imageLoader, dataLoader) {
        if (brandFile.isRegularFile()) {
            brand(
                try {
                    Json.decodeFromString(BrandConfig.serializer(), brandFile.readText())
                } catch (e: Exception) {
                    throw IllegalArgumentException("$brandFile: ${e.message}", e)
                },
            )
        }
        screensFrom(dataDir.resolve("screens"))
    }
}

val demoBrand = BrandConfig(
    name = "ComposeCast Demo",
    primaryColor = "#6200EE",
    secondaryColor = "#03DAC6",
    backgroundColor = "#FAFAFA",
)

/**
 * The demo screens (the JSON copy in `examples/demo/` must render identically —
 * `JsonScreensTest`; update both). A function so that screenshot tests (`compat-tests`) render exactly these
 * screens with a fake [ImageLoader] and [DataLoader], without network access.
 */
fun demoScreens(imageLoader: ImageLoader = HttpImageLoader, dataLoader: DataLoader = HttpDataLoader): ScreenRegistry =
    composeCast(imageLoader, dataLoader) {
        brand(demoBrand)

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
        }

        // Data sources and bindings (T-006): live data from Open-Meteo (keyless), rendered per request;
        // `/screens/data?lat=…&lon=…&city=…` shows another place. A generic demo of the feature —
        // real apps define such screens in their own repositories (ADR 0017).
        screen("data") {
            title = "{{params.city}}"
            param("lat", default = "52.52")
            param("lon", default = "13.41")
            param("city", default = "Berlin")
            data(
                "meteo",
                url = "https://api.open-meteo.com/v1/forecast?latitude={{params.lat}}&longitude={{params.lon}}" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                    "&forecast_days=3&timezone=auto",
                cacheSeconds = 600,
            )
            lookup(
                "wmo",
                mapOf(
                    "0" to "Clear sky",
                    "1" to "Mainly clear",
                    "2" to "Partly cloudy",
                    "3" to "Overcast",
                    "45" to "Fog",
                    "61" to "Rain",
                    "71" to "Snow",
                    "95" to "Thunderstorm",
                    "*" to "Changing weather",
                ),
            )
            text("Now", style = "caption")
            text("{{meteo.current.temperature_2m | round}}{{meteo.current_units.temperature_2m}}", style = "h1")
            text("{{meteo.current.weather_code | lookup:wmo}}")
            spacer(16)
            text("Next days", style = "caption")
            repeat(
                zip = mapOf(
                    "day" to "meteo.daily.time",
                    "max" to "meteo.daily.temperature_2m_max",
                    "min" to "meteo.daily.temperature_2m_min",
                ),
                alias = "d",
                empty = { text("No forecast") },
            ) {
                text("{{d.day | date:'EEEE'}}: {{d.max | round}}° / {{d.min | round}}°")
            }
            spacer(16)
            button("Warsaw", Action.Navigate("data", mapOf("lat" to "52.23", "lon" to "21.01", "city" to "Warsaw")))
            error {
                text("No data for {{params.city}}", style = "h3")
                text("The data source did not answer. Try again in a moment.")
                spacer(8)
                button("Retry", Action.Reload)
            }
        }

        screen("onboarding") {
            title = "Get Started"
            text("How it works", style = "h2")
            text("The server defines your UI. The app renders it.", style = "body")
            spacer(16)
            expandable("Local state on device", expanded = true) {
                text("Both states are in the document. Tap the header to collapse or expand without a server request.")
            }
            spacer(8)
            expandable("Wire format") {
                text("The server compiles screens to binary Remote Compose documents played natively on Android.")
            }
            spacer(16)
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
            button("Navigate with params", Action.Navigate("echo", mapOf("word" to "zażółć gęślą jaźń")))
            spacer(8)
            button("Back to home", Action.Navigate("main"))
        }

        // Rendered per request from its query parameters (`/screens/echo?word=…`) by Kotlin code: a DSL
        // dynamic screen has no JSON form (JSON screens use params/bindings instead, like `data`).
        dynamicScreen("echo") { ctx ->
            val word = ctx.param("word")
            title = "Echo"
            text("You sent", style = "caption")
            text(word, style = "h2")
            spacer(8)
            button("Again, louder", Action.Navigate("echo", mapOf("word" to "$word!")))
        }
    }
