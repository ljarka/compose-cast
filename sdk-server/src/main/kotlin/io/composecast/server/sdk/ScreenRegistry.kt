package io.composecast.server.sdk

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentJson
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import io.composecast.server.sdk.render.CustomEntry
import io.composecast.server.sdk.render.RemoteComposeRenderer
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/** A screen rendered to a Remote Compose binary document, ready to be served. */
public class RemoteScreen internal constructor(
    /** The source the document was rendered from (DSL or JSON); `ScreenJson.encode` exports it. */
    public val definition: ScreenDefinition,
    private val bytes: ByteArray,
) {
    public val id: String get() = definition.id
    public val title: String get() = definition.title

    /** The Remote Compose document. Returns a copy, so callers cannot mutate the cached one. */
    public val document: ByteArray get() = bytes.copyOf()

    public val size: Int get() = bytes.size

    /**
     * Strong HTTP entity tag (without quotes): a hash of the title and document bytes, so it
     * changes whenever anything that rendering read (title, params, data, brand, images) changes.
     */
    public val etag: String by lazy {
        MessageDigest.getInstance("SHA-256").apply {
            update(title.toByteArray(Charsets.UTF_8))
            update(0)
            update(bytes)
        }.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    /** Human-readable dump of the decoded document, for debugging. */
    public fun describe(): String = RemoteComposeRenderer.describe(bytes)

    /** Hash of what a data update cannot change ([RemoteComposeRenderer.structure], ADR 0029). */
    public val structure: String by lazy { RemoteComposeRenderer.structure(bytes) }

    internal fun sameBytes(other: RemoteScreen): Boolean = bytes.contentEquals(other.bytes)
}

/**
 * What a render may depend on. Today the request's query parameters; README Etap 12 (targeting)
 * will add client attributes. No cache key needs maintaining: [RemoteScreen.etag] hashes the output.
 */
public data class RenderContext(
    public val params: Map<String, String> = emptyMap(),
    public val rcApiLevel: Int? = null,
    public val rcProfiles: Int? = null,
) {
    /** A required parameter; missing → [ScreenParameterException] (the server answers 400). */
    public fun param(name: String): String = params[name] ?: throw ScreenParameterException("Missing parameter '$name'")

    public companion object {
        public val EMPTY: RenderContext = RenderContext()
    }
}

/** The request lacks or has an invalid parameter: a client error (HTTP 400), not a server bug. */
public class ScreenParameterException(message: String) : IllegalArgumentException(message)

/** The client's Remote Compose player is too old for the requested document (HTTP 426). */
public class UnsupportedClientVersionException(public val minApiLevel: Int, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * All screens of an app. Static screens (DSL `screen`, JSON) are rendered and validated once at
 * build; bound screens (params, data sources, `{{ }}` bindings — T-006) are validated at build and
 * fetched, bound and rendered on every [render]; dynamic screens (`dynamicScreen`) are built,
 * validated and rendered on every [render]. Thread-safe.
 */
public class ScreenRegistry internal constructor(
    public val brand: BrandConfig?,
    private val ids: List<String>,
    private val static: Map<String, RemoteScreen>,
    private val bound: Map<String, BoundScreen>,
    private val dynamic: Map<String, (RenderContext) -> ScreenDefinition>,
    private val renderer: RemoteComposeRenderer,
    private val fetcher: DataFetcher,
    private val components: Map<String, ComponentDefinition> = emptyMap(),
    private val brokenComponents: Map<String, String> = emptyMap(),
) {
    /** Custom component definitions (ADR 0033), by id. */
    public fun components(): Map<String, ComponentDefinition> = components

    /** Components that failed re-validation after an RC upgrade (id → why); screens skip them. */
    public fun brokenComponents(): Map<String, String> = brokenComponents

    /** The pre-rendered document of a static screen; `null` for unknown or dynamic screens. */
    public fun get(id: String): RemoteScreen? = static[id]

    /** Every screen id, static and dynamic, in definition order. */
    public fun ids(): List<String> = ids

    /** The pre-rendered static screens. */
    public fun all(): Map<String, RemoteScreen> = static

    /**
     * The authored definitions of static and bound screens (not of DSL dynamic screens, which
     * exist only per request), in definition order; `ScreenJson.encode` exports them.
     */
    public fun definitions(): Map<String, ScreenDefinition> = ids.mapNotNull { id ->
        (static[id]?.definition ?: bound[id]?.definition)?.let { id to it }
    }.toMap()

    public fun isDynamic(id: String): Boolean = id in dynamic

    /** Rendered per request from data sources/params (T-006). */
    public fun isBound(id: String): Boolean = id in bound

    private val staticByVersion = ConcurrentHashMap<Triple<String, Int, Int>, RemoteScreen>()

    /**
     * The document for [id] in [context]: the cached one for static screens (keyed by RC API
     * level/profiles when negotiated), a fresh render for bound and dynamic ones. `null` = unknown
     * id. Blocks while data sources are fetched: call it off the request thread (server-app uses
     * `Dispatchers.IO`).
     * @throws ScreenParameterException when a required parameter is missing (HTTP 400)
     * @throws DataSourceException when a data source fails and the screen has no `error` components (HTTP 502)
     * @throws UnsupportedClientVersionException when the client's RC API level is below minimum
     * or unsupported (HTTP 426)
     */
    public fun render(id: String, context: RenderContext = RenderContext.EMPTY): RemoteScreen? {
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
    public fun dataUpdate(screen: RemoteScreen, context: RenderContext = RenderContext.EMPTY): ByteArray {
        val apiLevel =
            minOf(context.rcApiLevel ?: RemoteComposeRenderer.MAX_API_LEVEL, RemoteComposeRenderer.MAX_API_LEVEL)
        val profiles = context.rcProfiles ?: RemoteComposeRenderer.DEFAULT_PROFILES
        return renderer.render(screen.definition, apiLevel, profiles, dataUpdate = true)
    }

    private fun render(definition: ScreenDefinition, apiLevel: Int, profiles: Int) =
        RemoteScreen(definition, renderer.render(definition, apiLevel, profiles))
}

@ScreenDslMarker
public class RegistryBuilder internal constructor(
    private val imageLoader: ImageLoader,
    private val fetcher: DataFetcher,
) {
    private val screens = linkedMapOf<String, ScreenDefinition>()
    private val boundScreens = linkedMapOf<String, BoundScreen>()
    private val dynamicScreens = linkedMapOf<String, (RenderContext) -> ScreenDefinition>()
    private val ids = mutableListOf<String>()
    private val components = linkedMapOf<String, ComponentDefinition>()
    private var brand: BrandConfig? = null

    private fun claim(id: String) {
        require(id !in ids) { "Duplicate screen id '$id'" }
        ids += id
    }

    /** A screen written with the Kotlin DSL. */
    public fun screen(id: String, block: ScreenBuilder.() -> Unit) {
        screen(ScreenBuilder(id).apply(block).build())
    }

    /**
     * A screen from any other source (JSON, admin panel). Validated here; a bound screen also
     * checks its secrets (environment) and date patterns here.
     */
    public fun screen(definition: ScreenDefinition) {
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
    public fun dynamicScreen(id: String, block: ScreenBuilder.(RenderContext) -> Unit) {
        claim(id)
        dynamicScreens[id] = { context -> ScreenBuilder(id).apply { block(context) }.build() }
    }

    /**
     * Adds every `*.json` file in [directory] (not recursive, in file-name order) as a screen
     * ([ScreenJson] format). Each file must be named `<id>.json` (ADR 0011). Errors name the file
     * and the JSON path. Hidden files (`.x.json`, macOS `._x.json` copied by tar/scp) are skipped.
     */
    public fun screensFrom(directory: Path) {
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

    /** A custom component (raw RC JSON body, ADR 0033); checked at build, with the brand. */
    public fun component(definition: ComponentDefinition) {
        require(definition.id !in components) { "Duplicate component id '${definition.id}'" }
        components[definition.id] = definition
    }

    /**
     * Adds every `*.json` file in [directory] as a component ([ComponentJson]); each must be named
     * `<id>.json`. A missing directory adds nothing (components are optional).
     */
    public fun componentsFrom(directory: Path) {
        if (!directory.isDirectory()) return
        directory.listDirectoryEntries("*.json")
            .filterNot { it.fileName.toString().startsWith(".") }
            .sortedBy { it.fileName.toString() }
            .forEach { file ->
                val definition = ComponentJson.decode(file.readText(), source = file.toString())
                require(definition.id == file.nameWithoutExtension) {
                    "$file: declares id '${definition.id}', but the file must be named '${definition.id}.json'"
                }
                component(definition)
            }
    }

    public fun brand(
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

    public fun brand(config: BrandConfig) {
        brand = config
    }

    /** Renders every static screen once; screens are rendered after the brand is known. */
    internal fun build(): ScreenRegistry {
        val entries = checkComponents()
        checkUses(entries)
        val renderer = RemoteComposeRenderer(brand, imageLoader, entries)
        val rendered = screens.mapValues { (_, definition) -> RemoteScreen(definition, renderer.render(definition)) }
        return ScreenRegistry(
            brand,
            ids.toList(),
            rendered,
            boundScreens.toMap(),
            dynamicScreens.toMap(),
            renderer,
            fetcher,
            components.toMap(),
            entries.filterValues { it.broken != null }.mapValues { it.value.broken!! },
        )
    }

    /**
     * ADR 0033 versioning: a definition validated against this RC release must pass (else the build
     * fails, like a screen); one from another release that fails now is `broken` — screens skip it.
     */
    private fun checkComponents(): Map<String, CustomEntry> {
        val checker = RemoteComposeRenderer(brand, imageLoader)
        return components.mapValues { (id, definition) ->
            val problems = checker.componentProblems(definition)
            when {
                problems.isEmpty() -> {
                    if (definition.rcVersion != CustomComponents.RC_VERSION) {
                        log.info(
                            "Component '$id' (validated with RC ${definition.rcVersion}) still works with " +
                                "RC ${CustomComponents.RC_VERSION}",
                        )
                    }
                    CustomEntry(definition, null)
                }
                definition.rcVersion == CustomComponents.RC_VERSION ->
                    throw IllegalArgumentException("Component '$id': ${problems.joinToString("; ")}")
                else -> {
                    val why = "validated with RC ${definition.rcVersion}, fails with RC " +
                        "${CustomComponents.RC_VERSION}: ${problems.joinToString("; ")}"
                    log.warning("Component '$id' is broken and not rendered: $why")
                    CustomEntry(definition, why)
                }
            }
        }
    }

    /** Every `custom` in a static or bound screen names a known component with fitting arguments. */
    private fun checkUses(entries: Map<String, CustomEntry>) {
        (screens.values + boundScreens.values.map { it.definition }).forEach { screen ->
            val problems = CustomComponents.uses(screen).flatMap { (at, use) ->
                val definition = entries[use.component]?.definition
                    ?: return@flatMap listOf("$at.component: unknown custom component '${use.component}'")
                definition.argProblems(use.args, at)
            }
            require(problems.isEmpty()) { "Screen '${screen.id}': ${problems.joinToString("; ")}" }
        }
    }

    private companion object {
        val log: Logger = Logger.getLogger(RegistryBuilder::class.java.name)
    }
}

/**
 * Defines brand and screens and renders them to Remote Compose documents.
 * Images referenced by screens are fetched with [imageLoader] and embedded in the documents; data
 * sources (T-006) are fetched with [dataLoader]; `{{secret.NAME}}` reads `COMPOSECAST_SECRET_NAME`
 * from [env].
 */
public fun composeCast(
    imageLoader: ImageLoader = HttpImageLoader(),
    dataLoader: DataLoader = HttpDataLoader,
    env: (String) -> String? = System::getenv,
    block: RegistryBuilder.() -> Unit,
): ScreenRegistry = RegistryBuilder(imageLoader, DataFetcher(dataLoader, env)).apply(block).build()
