package io.composecast.server.sdk

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/** A screen rendered to a Remote Compose binary document, ready to be served. */
class RemoteScreen internal constructor(
    /** The source the document was rendered from (DSL or JSON); `ScreenJson.encode` exports it. */
    val definition: ScreenDefinition,
    private val bytes: ByteArray,
) {
    val id: String get() = definition.id
    val title: String get() = definition.title

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
class RegistryBuilder internal constructor(private val imageLoader: ImageLoader) {
    private val screens = linkedMapOf<String, ScreenDefinition>()
    private var brand: BrandConfig? = null

    /** A screen written with the Kotlin DSL. */
    fun screen(id: String, block: ScreenBuilder.() -> Unit) {
        screen(ScreenBuilder(id).apply(block).build())
    }

    /** A screen from any other source (JSON, admin panel). Validated here. */
    fun screen(definition: ScreenDefinition) {
        definition.validate()
        require(definition.id !in screens) { "Duplicate screen id '${definition.id}'" }
        screens[definition.id] = definition
    }

    /**
     * Adds every `*.json` file in [directory] (not recursive, in file-name order) as a screen
     * ([ScreenJson] format). Each file must be named `<id>.json` (ADR 0011). Errors name the file
     * and the JSON path.
     */
    fun screensFrom(directory: Path) {
        require(directory.isDirectory()) { "Screens directory '$directory' does not exist" }
        directory.listDirectoryEntries("*.json").sortedBy { it.fileName.toString() }.forEach { file ->
            val definition = ScreenJson.decode(file.readText(), source = file.toString())
            require(definition.id == file.nameWithoutExtension) {
                "$file: declares id '${definition.id}', but the file must be named '${definition.id}.json'"
            }
            screen(definition)
        }
    }

    fun brand(
        name: String,
        primaryColor: String,
        secondaryColor: String,
        backgroundColor: String = "#FFFFFF",
        fontFamily: String = "default",
    ) {
        brand(BrandConfig(name, primaryColor, secondaryColor, backgroundColor, fontFamily))
    }

    fun brand(config: BrandConfig) {
        brand = config
    }

    /** Renders every screen to Remote Compose once; screens are rendered after the brand is known. */
    internal fun build(): ScreenRegistry {
        val renderer = RemoteComposeRenderer(brand, imageLoader)
        val rendered = screens.mapValues { (_, definition) -> RemoteScreen(definition, renderer.render(definition)) }
        return ScreenRegistry(brand, rendered)
    }
}

/**
 * Defines brand and screens and renders them to Remote Compose documents.
 * Images referenced by screens are fetched with [imageLoader] and embedded in the documents.
 */
fun composeCast(
    imageLoader: ImageLoader = HttpImageLoader,
    block: RegistryBuilder.() -> Unit,
): ScreenRegistry = RegistryBuilder(imageLoader).apply(block).build()
