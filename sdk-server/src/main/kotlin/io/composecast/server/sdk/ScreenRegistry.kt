package io.composecast.server.sdk

import io.composecast.protocol.BrandConfig

/** A screen rendered to a Remote Compose binary document, ready to be served. */
class RemoteScreen internal constructor(
    val id: String,
    val title: String,
    private val bytes: ByteArray,
) {
    /** The Remote Compose document. Returns a copy, so callers cannot mutate the cached one. */
    val document: ByteArray get() = bytes.copyOf()

    val size: Int get() = bytes.size

    /** Human-readable dump of the decoded document, for debugging. */
    fun describe(): String = RemoteComposeRenderer.describe(bytes)
}

class ScreenRegistry internal constructor(
    val brand: BrandConfig?,
    private val screens: Map<String, RemoteScreen>,
) {
    fun get(id: String): RemoteScreen? = screens[id]

    fun ids(): List<String> = screens.keys.toList()

    fun all(): Map<String, RemoteScreen> = screens
}

@ScreenDslMarker
class RegistryBuilder internal constructor() {
    private val screens = linkedMapOf<String, ScreenBuilder>()
    private var brand: BrandConfig? = null

    fun screen(id: String, block: ScreenBuilder.() -> Unit) {
        require(id !in screens) { "Duplicate screen id '$id'" }
        screens[id] = ScreenBuilder(id).apply(block)
    }

    fun brand(
        name: String,
        primaryColor: String,
        secondaryColor: String,
        backgroundColor: String = "#FFFFFF",
        fontFamily: String = "default",
    ) {
        brand = BrandConfig(name, primaryColor, secondaryColor, backgroundColor, fontFamily)
    }

    /** Renders every screen to Remote Compose once; screens are rendered after the brand is known. */
    internal fun build(): ScreenRegistry {
        val renderer = RemoteComposeRenderer(brand)
        val rendered = screens.mapValues { (id, builder) ->
            RemoteScreen(id, builder.title, renderer.render(builder.nodes()))
        }
        return ScreenRegistry(brand, rendered)
    }
}

fun composeCast(block: RegistryBuilder.() -> Unit): ScreenRegistry =
    RegistryBuilder().apply(block).build()
