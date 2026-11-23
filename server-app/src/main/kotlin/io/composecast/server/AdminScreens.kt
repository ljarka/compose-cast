package io.composecast.server

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenJson
import io.composecast.server.admin.AdminAuth
import io.composecast.server.admin.PublishException
import io.composecast.server.admin.ScreenPublishing
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.HttpDataLoader
import io.composecast.server.sdk.HttpImageLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.RenderContext
import io.composecast.server.sdk.ScreenRegistry
import io.composecast.server.sdk.composeCast
import io.composecast.server.store.ScreenStore
import io.composecast.server.store.SettingsStore
import io.composecast.server.store.StoreDatabase
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Admin mode (T-007, ADR 0016): the database in the data directory is the source of truth. On the
 * first start it imports the directory (`screens/<id>.json` + `brand.json`) (validated like files
 * mode); afterwards the files are ignored and screens change only through the store.
 */
class AdminScreens(
    private val screens: ScreenStore,
    private val settings: SettingsStore,
    private val imageLoader: ImageLoader = HttpImageLoader(),
    private val dataLoader: DataLoader = HttpDataLoader,
    private val env: (String) -> String? = System::getenv,
) {
    /** Imports [dataDir] when the database has no screens yet; returns the number of imported screens. */
    fun importIfEmpty(dataDir: Path): Int {
        if (!screens.isEmpty() || !dataDir.resolve("screens").isDirectory()) return 0
        val files = loadScreens(dataDir, imageLoader, dataLoader) // validates everything first
        files.brand?.let { settings[SettingsStore.BRAND] = Json.encodeToString(BrandConfig.serializer(), it) }
        screens.publishAll(files.definitions().mapValues { ScreenJson.encode(it.value) }, by = IMPORT)
        log.info("Imported {} screens from {} into the admin database", files.ids().size, dataDir)
        return files.ids().size
    }

    /** The configured home screen id (served for `main`), or null when `main` itself is used. */
    fun homeScreen(): String? = settings[SettingsStore.HOME_SCREEN]?.takeIf { it.isNotBlank() }

    /** Sets [id] as the home screen (served for `main`) if it is currently live. */
    fun setHome(id: String, serve: (String?) -> Unit) = synchronized(publishLock) {
        require(screens.live().containsKey(id)) { "Screen '$id' must be published before setting it as home" }
        settings[SettingsStore.HOME_SCREEN] = id
        serve(id)
    }

    /** Removes screen [id] (refusing the current home screen) and [serve]s the remaining live screens. */
    fun delete(id: String, serve: (ScreenRegistry) -> Unit) = synchronized(publishLock) {
        val home = homeScreen() ?: "main"
        require(id != home) { "Cannot remove the home screen '$id'; set another screen as home first" }
        val registry = build(screens.live() - id)
        screens.delete(id)
        serve(registry)
    }

    /** The registry of the live screens and brand in the database. */
    fun load(): ScreenRegistry = build(screens.live())

    /** Serializes publishes: build → write → serve must not interleave (a slower one would win with stale data). */
    private val publishLock = Any()

    /**
     * T-007 A4: makes [json] the live version of [id] **only if** all live screens still build with
     * it (e.g. a `{{secret.X}}` must be set); then [serve]s the new registry. On failure nothing changed.
     */
    fun publish(id: String, json: String, by: String, serve: (ScreenRegistry) -> Unit) = synchronized(publishLock) {
        val registry = build(screens.live() + (id to json))
        screens.publish(id, json, by)
        serve(registry)
    }

    /** Like [publish] for revision [revisionId] of [id] (a pending draft is kept). */
    fun rollback(id: String, revisionId: Int, by: String, serve: (ScreenRegistry) -> Unit) = synchronized(publishLock) {
        val revision = requireNotNull(screens.revision(id, revisionId)) { "Screen '$id' has no revision $revisionId" }
        val registry = build(screens.live() + (id to revision.json))
        screens.rollback(id, revisionId, by)
        serve(registry)
    }

    /**
     * T-007 A5: makes [brandJson] (`BrandConfig` JSON) the brand of every screen and `/brand`, like
     * [publish]: built first (all static screens re-render with it), written only if that works.
     */
    fun publishBrand(brandJson: String, serve: (ScreenRegistry) -> Unit) = synchronized(publishLock) {
        val brand = Json.decodeFromString(BrandConfig.serializer(), brandJson)
        require(brand.problems().isEmpty()) { brand.problems().joinToString("; ") }
        val registry = build(screens.live(), brandJson)
        settings[SettingsStore.BRAND] = brandJson
        serve(registry)
    }

    /**
     * T-045 B2: a one-screen registry serving [json] (checked like a draft) as [previewId], with the
     * live brand; nothing is written. Navigation from it goes to the live screens.
     */
    fun preview(json: String, previewId: String): ScreenRegistry = composeCast(imageLoader, dataLoader, env) {
        settings[SettingsStore.BRAND]?.let { brand(Json.decodeFromString(BrandConfig.serializer(), it)) }
        screen(ScreenJson.decode(json, source = "preview").copy(id = previewId))
    }

    /** Renders [json] once (with default param values) and returns the decoded Remote Compose tree. */
    fun describe(json: String): String {
        val definition = ScreenJson.decode(json, source = "describe")
        val registry = composeCast(imageLoader, dataLoader, env) {
            settings[SettingsStore.BRAND]?.let { brand(Json.decodeFromString(BrandConfig.serializer(), it)) }
            screen(definition)
        }
        val defaults = definition.params.mapValues { it.value.default.orEmpty() }
        val rendered = requireNotNull(registry.render(definition.id, RenderContext(params = defaults)))
        return "size: ${rendered.size} bytes\netag: ${rendered.etag}\n\n${rendered.describe()}"
    }

    /** Screens in id order, as [load] after a restart would serve them. */
    private fun build(live: Map<String, String>, brandJson: String? = settings[SettingsStore.BRAND]): ScreenRegistry =
        composeCast(imageLoader, dataLoader, env) {
            brandJson?.let { brand(Json.decodeFromString(BrandConfig.serializer(), it)) }
            live.toSortedMap().forEach { (id, json) -> screen(ScreenJson.decode(json, source = "screen '$id'")) }
        }

    companion object {
        /** `published_by` of rows created by the first-start import. */
        const val IMPORT = "import"
        const val DATABASE_FILE = "compose-cast.db"
        private val log = LoggerFactory.getLogger(AdminScreens::class.java)

        fun openDatabase(dataDir: Path): StoreDatabase = StoreDatabase.open(dataDir.resolve(DATABASE_FILE))

        fun open(
            dataDir: Path,
            imageLoader: ImageLoader = HttpImageLoader(),
            dataLoader: DataLoader = HttpDataLoader,
        ): AdminScreens {
            val database = openDatabase(dataDir)
            return AdminScreens(ScreenStore(database), SettingsStore(database), imageLoader, dataLoader)
        }
    }
}

/**
 * The first admin account from `COMPOSECAST_ADMIN_EMAIL` / `COMPOSECAST_ADMIN_PASSWORD` (ADR 0016):
 * required while there is no account (fail fast: nobody could log in), ignored afterwards.
 */
internal fun AdminAuth.bootstrapFromEnv(email: String?, password: String?) {
    val log = LoggerFactory.getLogger(AdminScreens::class.java)
    if (hasAccounts()) {
        if (email != null) log.info("Admin accounts exist; COMPOSECAST_ADMIN_EMAIL/PASSWORD are ignored")
        return
    }
    require(!email.isNullOrBlank() && !password.isNullOrEmpty()) {
        "COMPOSECAST_MODE=admin has no account yet: set COMPOSECAST_ADMIN_EMAIL and COMPOSECAST_ADMIN_PASSWORD " +
            "(at least ${AdminAuth.MIN_PASSWORD_LENGTH} characters) for the first admin"
    }
    bootstrapAdmin(email, password)
    log.info("Created the first admin account {}", email.trim().lowercase())
}

/** [ScreenPublishing] for `server-admin`: publishes through [AdminScreens] and swaps [live]. */
class AdminPublishing(private val admin: AdminScreens, private val live: LiveScreens) : ScreenPublishing {
    override fun publish(id: String, json: String, by: String) = failsAsPublishException {
        admin.publish(id, json, by) { registry ->
            live.replace(registry)
            live.replaceHome(admin.homeScreen())
        }
    }

    override fun rollback(id: String, revisionId: Int, by: String) = failsAsPublishException {
        admin.rollback(id, revisionId, by) { registry ->
            live.replace(registry)
            live.replaceHome(admin.homeScreen())
        }
    }

    override fun publishBrand(brandJson: String) = failsAsPublishException {
        admin.publishBrand(brandJson, live::replace)
    }

    override fun preview(json: String): String {
        val id = live.previews.newId()
        failsAsPublishException { live.previews.put(id, admin.preview(json, id)) }
        return id
    }

    override fun setHome(id: String) = failsAsPublishException {
        admin.setHome(id, live::replaceHome)
    }

    override fun delete(id: String) = failsAsPublishException {
        admin.delete(id, live::replace)
    }

    override fun describe(json: String): String {
        var result = ""
        failsAsPublishException { result = admin.describe(json) }
        return result
    }

    /** Building fails with IllegalArgumentException/IllegalStateException (validation, secrets, dates). */
    private inline fun failsAsPublishException(block: () -> Unit) = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw PublishException(e.message ?: "invalid screen", e)
    } catch (e: IllegalStateException) {
        throw PublishException(e.message ?: "invalid screen", e)
    }
}
