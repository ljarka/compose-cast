package io.composecast.server.sdk

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import java.nio.file.Path
import java.security.MessageDigest
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

    /**
     * Strong HTTP entity tag (without quotes): a hash of the document bytes, so it changes whenever
     * anything that rendering read (params, data, brand, images) changes the output.
     */
    val etag: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(bytes).take(16).joinToString("") { "%02x".format(it) }
    }

    /** Human-readable dump of the decoded document, for debugging. */
    fun describe(): String = RemoteComposeRenderer.describe(bytes)
}

/**
 * What a render may depend on. Today the request's query parameters; README Etap 12 (targeting)
 * will add client attributes. No cache key needs maintaining: [RemoteScreen.etag] hashes the output.
 */
data class RenderContext(val params: Map<String, String> = emptyMap()) {
    /** A required parameter; missing → [ScreenParameterException] (the server answers 400). */
    fun param(name: String): String = params[name] ?: throw ScreenParameterException("Missing parameter '$name'")

    companion object {
        val EMPTY = RenderContext()
    }
}

/** The request lacks or has an invalid parameter: a client error (HTTP 400), not a server bug. */
class ScreenParameterException(message: String) : IllegalArgumentException(message)

/**
 * All screens of an app. Static screens (DSL `screen`, JSON) are rendered and validated once at
 * build; dynamic screens (`dynamicScreen`) are built, validated and rendered on every [render].
 * Thread-safe.
 */
class ScreenRegistry internal constructor(
    val brand: BrandConfig?,
    private val ids: List<String>,
    private val static: Map<String, RemoteScreen>,
    private val dynamic: Map<String, (RenderContext) -> ScreenDefinition>,
    private val renderer: RemoteComposeRenderer,
) {
    /** The pre-rendered document of a static screen; `null` for unknown or dynamic screens. */
    fun get(id: String): RemoteScreen? = static[id]

    /** Every screen id, static and dynamic, in definition order. */
    fun ids(): List<String> = ids

    /** The pre-rendered static screens. */
    fun all(): Map<String, RemoteScreen> = static

    fun isDynamic(id: String): Boolean = id in dynamic

    /**
     * The document for [id] in [context]: the cached one for static screens (they ignore params),
     * a fresh render for dynamic ones. `null` = unknown id.
     * @throws ScreenParameterException when a dynamic screen lacks a required parameter
     */
    fun render(id: String, context: RenderContext = RenderContext.EMPTY): RemoteScreen? {
        static[id]?.let { return it }
        val build = dynamic[id] ?: return null
        val definition = build(context).also { it.validate() }
        return RemoteScreen(definition, renderer.render(definition))
    }
}

@ScreenDslMarker
class RegistryBuilder internal constructor(private val imageLoader: ImageLoader) {
    private val screens = linkedMapOf<String, ScreenDefinition>()
    private val dynamicScreens = linkedMapOf<String, (RenderContext) -> ScreenDefinition>()
    private val ids = mutableListOf<String>()
    private var brand: BrandConfig? = null

    private fun claim(id: String) {
        require(id !in ids) { "Duplicate screen id '$id'" }
        ids += id
    }

    /** A screen written with the Kotlin DSL. */
    fun screen(id: String, block: ScreenBuilder.() -> Unit) {
        screen(ScreenBuilder(id).apply(block).build())
    }

    /** A screen from any other source (JSON, admin panel). Validated here. */
    fun screen(definition: ScreenDefinition) {
        definition.validate()
        claim(definition.id)
        screens[definition.id] = definition
    }

    /**
     * A screen built per request from its [RenderContext] (query parameters), e.g.
     * `dynamicScreen("movie") { ctx -> text("Movie ${ctx.param("id")}") }`. Rendered on every
     * request; its content is validated then (invalid content = HTTP 500, missing param = 400).
     */
    fun dynamicScreen(id: String, block: ScreenBuilder.(RenderContext) -> Unit) {
        claim(id)
        dynamicScreens[id] = { context -> ScreenBuilder(id).apply { block(context) }.build() }
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

    /** Renders every static screen once; screens are rendered after the brand is known. */
    internal fun build(): ScreenRegistry {
        val renderer = RemoteComposeRenderer(brand, imageLoader)
        val rendered = screens.mapValues { (_, definition) -> RemoteScreen(definition, renderer.render(definition)) }
        return ScreenRegistry(brand, ids.toList(), rendered, dynamicScreens.toMap(), renderer)
    }
}

/**
 * Defines brand and screens and renders them to Remote Compose documents.
 * Images referenced by screens are fetched with [imageLoader] and embedded in the documents.
 */
fun composeCast(imageLoader: ImageLoader = HttpImageLoader, block: RegistryBuilder.() -> Unit): ScreenRegistry =
    RegistryBuilder(imageLoader).apply(block).build()
