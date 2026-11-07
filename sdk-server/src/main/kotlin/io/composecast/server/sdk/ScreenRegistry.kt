package io.composecast.server.sdk

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenModel

class ScreenRegistry {
    private val screens = mutableMapOf<String, ScreenModel>()
    var brand: BrandConfig? = null

    fun register(screen: ScreenModel) {
        screens[screen.id] = screen
    }

    operator fun plusAssign(screen: ScreenModel) = register(screen)

    fun get(id: String): ScreenModel? = screens[id]

    fun all(): Map<String, ScreenModel> = screens.toMap()
}

@ScreenDslMarker
class RegistryBuilder {
    private val registry = ScreenRegistry()

    fun screen(id: String, block: ScreenBuilder.() -> Unit) {
        registry.register(io.composecast.server.sdk.screen(id, block))
    }

    fun brand(
        name: String,
        primaryColor: String,
        secondaryColor: String,
        backgroundColor: String = "#FFFFFF",
        fontFamily: String = "default",
    ) {
        registry.brand = BrandConfig(name, primaryColor, secondaryColor, backgroundColor, fontFamily)
    }

    fun build(): ScreenRegistry = registry
}

fun composeCast(block: RegistryBuilder.() -> Unit): ScreenRegistry =
    RegistryBuilder().apply(block).build()
