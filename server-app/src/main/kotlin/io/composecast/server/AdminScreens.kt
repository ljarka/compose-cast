package io.composecast.server

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenJson
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.HttpDataLoader
import io.composecast.server.sdk.HttpImageLoader
import io.composecast.server.sdk.ImageLoader
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

    /** The registry of the live screens and brand in the database. */
    fun load(): ScreenRegistry = composeCast(imageLoader, dataLoader) {
        settings[SettingsStore.BRAND]?.let { brand(Json.decodeFromString(BrandConfig.serializer(), it)) }
        screens.live().forEach { (id, json) -> screen(ScreenJson.decode(json, source = "database screen '$id'")) }
    }

    companion object {
        /** `published_by` of rows created by the first-start import. */
        const val IMPORT = "import"
        const val DATABASE_FILE = "compose-cast.db"
        private val log = LoggerFactory.getLogger(AdminScreens::class.java)

        fun open(
            dataDir: Path,
            imageLoader: ImageLoader = HttpImageLoader(),
            dataLoader: DataLoader = HttpDataLoader,
        ): AdminScreens {
            val database = StoreDatabase.open(dataDir.resolve(DATABASE_FILE))
            return AdminScreens(ScreenStore(database), SettingsStore(database), imageLoader, dataLoader)
        }
    }
}
