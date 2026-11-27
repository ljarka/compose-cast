package io.composecast.server

import io.composecast.protocol.BrandConfig
import io.composecast.server.demo.actionsScreen
import io.composecast.server.demo.adaptiveScreen
import io.composecast.server.demo.collectionsScreen
import io.composecast.server.demo.componentsScreen
import io.composecast.server.demo.customScreen
import io.composecast.server.demo.dashboardScreen
import io.composecast.server.demo.dataScreen
import io.composecast.server.demo.demoComponents
import io.composecast.server.demo.echoScreen
import io.composecast.server.demo.interactiveScreen
import io.composecast.server.demo.layoutsScreen
import io.composecast.server.demo.liveScreen
import io.composecast.server.demo.mainScreen
import io.composecast.server.demo.onboardingScreen
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
 * `BrandConfig`, optional `components/<id>.json` custom components (ADR 0033); ADR 0011 layout) or,
 * when it is null, the built-in [demoScreens].
 * Renders everything (fetching images), so call it once at startup.
 */
fun loadScreens(
    dataDir: Path?,
    imageLoader: ImageLoader = HttpImageLoader(),
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
        componentsFrom(dataDir.resolve("components"))
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
fun demoScreens(imageLoader: ImageLoader = HttpImageLoader(), dataLoader: DataLoader = HttpDataLoader): ScreenRegistry =
    composeCast(imageLoader, dataLoader) {
        brand(demoBrand)
        demoComponents()

        mainScreen()
        layoutsScreen()
        dataScreen()
        onboardingScreen()
        actionsScreen()
        echoScreen()
        componentsScreen()
        collectionsScreen()
        adaptiveScreen()
        dashboardScreen()
        liveScreen()
        interactiveScreen()
        customScreen()
    }
