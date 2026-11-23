package io.composecast.server.store

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Key/value app settings (T-007 A5); values are opaque text (e.g. the brand as `brand.json` text). */
class SettingsStore(private val database: StoreDatabase) {
    operator fun get(key: String): String? = database.tx {
        AppSettings.selectAll().where { AppSettings.key eq key }.singleOrNull()?.get(AppSettings.value)
    }

    operator fun set(key: String, value: String) {
        database.tx {
            val updated = AppSettings.update({ AppSettings.key eq key }) { it[AppSettings.value] = value }
            if (updated == 0) {
                AppSettings.insert {
                    it[AppSettings.key] = key
                    it[AppSettings.value] = value
                }
            }
        }
    }

    companion object {
        /** The brand, as the text of a `brand.json` file (`BrandConfig`). */
        const val BRAND = "brand"
    }
}
