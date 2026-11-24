package io.composecast.server.sdk

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import io.composecast.server.sdk.render.RemoteComposeRenderer
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
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
     * Strong HTTP entity tag (without quotes): a hash of the title and document bytes, so it
     * changes whenever anything that rendering read (title, params, data, brand, images) changes.
     */
    val etag: String by lazy {
        MessageDigest.getInstance("SHA-256").apply {
            update(title.toByteArray(Charsets.UTF_8))
            update(0)
            update(bytes)
        }.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    /** Human-readable dump of the decoded document, for debugging. */
    fun describe(): String = RemoteComposeRenderer.describe(bytes)

    /** Hash of what a data update cannot change ([RemoteComposeRenderer.structure], ADR 0029). */
    val structure: String by lazy { RemoteComposeRenderer.structure(bytes) }

    internal fun sameBytes(other: RemoteScreen): Boolean = bytes.contentEquals(other.bytes)
}

/**
 * What a render may depend on. Today the request's query parameters; README Etap 12 (targeting)
 * will add client attributes. No cache key needs maintaining: [RemoteScreen.etag] hashes the output.
 */
data class RenderContext(
    val params: Map<String, String> = emptyMap(),
    val rcApiLevel: Int? = null,
    val rcProfiles: Int? = null,
) {
    /** A required parameter; missing → [ScreenParameterException] (the server answers 400). */
    fun param(name: String): String = params[name] ?: throw ScreenParameterException("Missing parameter '$name'")

    companion object {
        val EMPTY = RenderContext()
    }
}

/** The request lacks or has an invalid parameter: a client error (HTTP 400), not a server bug. */
class ScreenParameterException(message: String) : IllegalArgumentException(message)

/** The client's Remote Compose player is too old for the requested document (HTTP 426). */
class UnsupportedClientVersionException(val minApiLevel: Int, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * All screens of an app. Static screens (DSL `screen`, JSON) are rendered and validated once at
 * build; bound screens (params, data sources, `{{ }}` bindings — T-006) are validated at build and
 * fetched, bound and rendered on every [render]; dynamic screens (`dynamicScreen`) are built,
 * validated and rendered on every [render]. Thread-safe.
 */
class ScreenRegistry internal constructor(
    val brand: BrandConfig?,
    private val ids: List<String>,
    private val static: Map<String, RemoteScreen>,
    private val bound: Map<String, BoundScreen>,
    private val dynamic: Map<String, (RenderContext) -> ScreenDefinition>,
    private val renderer: RemoteComposeRenderer,
    private val fetcher: DataFetcher,
) {
    /** The pre-rendered document of a static screen; `null` for unknown or dynamic screens. */
    fun get(id: String): RemoteScreen? = static[id]

    /** Every screen id, static and dynamic, in definition order. */
    fun ids(): List<String> = ids

    /** The pre-rendered static screens. */
    fun all(): Map<String, RemoteScreen> = static

    /**
     * The authored definitions of static and bound screens (not of DSL dynamic screens, which
     * exist only per request), in definition order; `ScreenJson.encode` exports them.
     */
    fun definitions(): Map<String, ScreenDefinition> = ids.mapNotNull { id ->
        (static[id]?.definition ?: bound[id]?.definition)?.let { id to it }
    }.toMap()

    fun isDynamic(id: String): Boolean = id in dynamic

    /** Rendered per request from data sources/params (T-006). */
    fun isBound(id: String): Boolean = id in bound

    private val staticByVersion = ConcurrentHashMap<Triple<String, Int, Int>, RemoteScreen>()

    /**
     * The document for [id] in [context]: the cached one for static screens (keyed by RC API
     * level/profiles when negotiated), a fresh render for bound and dynamic ones. `null` = unknown
     * id. Blocks while data sources are fetched: call it off the request thread (server-app uses
     * `Dispatchers.IO`).
     * @throws ScreenParameterException when a required parameter is missing (HTTP 400)
     * @throws DataSourceException when a data source fails and the screen has no `error` components (HTTP 502)
     * @throws UnsupportedClientVersionException when the client's RC API level is below minimum or unsupported (HTTP 426)
     */
    fun render(id: String, context: RenderContext = RenderContext.EMPTY): RemoteScreen? {
        val apiLevel = context.rcApiLevel ?: RemoteComposeRenderer.MAX_API_LEVEL
        val profiles = context.rcProfiles ?: RemoteComposeRenderer.DEFAULT_PROFILES
        if (apiLevel < RemoteComposeRenderer.MIN_API_LEVEL) {
            throw UnsupportedClientVersionException(
                RemoteComposeRenderer.MIN_API_LEVEL,
                "Client RC API level $apiLevel is below minimum supported ${RemoteComposeRenderer.MIN_API_LEVEL}",
            )
        }
        val targetLevel = minOf(apiLevel, RemoteComposeRenderer.MAX_API_LEVEL)
        static[id]?.let { defaultScreen ->
            if (targetLevel == RemoteComposeRenderer.MAX_API_LEVEL &&
                profiles == RemoteComposeRenderer.DEFAULT_PROFILES
            ) {
                return defaultScreen
            }
            return staticByVersion.computeIfAbsent(Triple(id, targetLevel, profiles)) {
                render(defaultScreen.definition, targetLevel, profiles)
            }
        }
        bound[id]?.let { return render(it.bind(context), targetLevel, profiles) }
        val build = dynamic[id] ?: return null
        val definition = build(context).also { it.validate() }
        val resolved = if (definition.isBound) BoundScreen(definition, fetcher).bind(context) else definition
        return render(resolved, targetLevel, profiles)
    }

    /**
     * [screen] (a result of [render] with the same [context]) as a data-update document: the same
     * definition, already bound, so no data is fetched again and the ids match (ADR 0029).
     */
    fun dataUpdate(screen: RemoteScreen, context: RenderContext = RenderContext.EMPTY): ByteArray {
        val apiLevel =
            minOf(context.rcApiLevel ?: RemoteComposeRenderer.MAX_API_LEVEL, RemoteComposeRenderer.MAX_API_LEVEL)
        val profiles = context.rcProfiles ?: RemoteComposeRenderer.DEFAULT_PROFILES
        return renderer.render(screen.definition, apiLevel, profiles, dataUpdate = true)
    }

    private fun render(definition: ScreenDefinition, apiLevel: Int, profiles: Int) =
        RemoteScreen(definition, renderer.render(definition, apiLevel, profiles))
}

@ScreenDslMarker
class RegistryBuilder internal constructor(private val imageLoader: ImageLoader, private val fetcher: DataFetcher) {
    private val screens = linkedMapOf<String, ScreenDefinition>()
    private val boundScreens = linkedMapOf<String, BoundScreen>()
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

    /**
     * A screen from any other source (JSON, admin panel). Validated here; a bound screen also
     * checks its secrets (environment) and date patterns here.
     */
    fun screen(definition: ScreenDefinition) {
        definition.validate()
        val bound = if (definition.isBound) BoundScreen(definition, fetcher) else null
        claim(definition.id)
        if (bound != null) boundScreens[definition.id] = bound else screens[definition.id] = definition
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
     * and the JSON path. Hidden files (`.x.json`, macOS `._x.json` copied by tar/scp) are skipped.
     */
    fun screensFrom(directory: Path) {
        require(directory.isDirectory()) { "Screens directory '$directory' does not exist" }
        directory.listDirectoryEntries("*.json")
            .filterNot { it.fileName.toString().startsWith(".") }
            .sortedBy { it.fileName.toString() }
            .forEach { file ->
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
        surfaceColor: String? = null,
        textColor: String? = null,
        outlineColor: String? = null,
    ) {
        brand(
            BrandConfig(
                name,
                primaryColor,
                secondaryColor,
                backgroundColor,
                fontFamily,
                surfaceColor,
                textColor,
                outlineColor,
            ),
        )
    }

    fun brand(config: BrandConfig) {
        brand = config
    }

    /** Renders every static screen once; screens are rendered after the brand is known. */
    internal fun build(): ScreenRegistry {
        val renderer = RemoteComposeRenderer(brand, imageLoader)
        val rendered = screens.mapValues { (_, definition) -> RemoteScreen(definition, renderer.render(definition)) }
        return ScreenRegistry(
            brand,
            ids.toList(),
            rendered,
            boundScreens.toMap(),
            dynamicScreens.toMap(),
            renderer,
            fetcher,
        )
    }
}

/**
 * Defines brand and screens and renders them to Remote Compose documents.
 * Images referenced by screens are fetched with [imageLoader] and embedded in the documents; data
 * sources (T-006) are fetched with [dataLoader]; `{{secret.NAME}}` reads `COMPOSECAST_SECRET_NAME`
 * from [env].
 */
fun composeCast(
    imageLoader: ImageLoader = HttpImageLoader(),
    dataLoader: DataLoader = HttpDataLoader,
    env: (String) -> String? = System::getenv,
    block: RegistryBuilder.() -> Unit,
): ScreenRegistry = RegistryBuilder(imageLoader, DataFetcher(dataLoader, env)).apply(block).build()
