package io.composecast.templates

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `manifest.json` of an app bundle (T-030, ADR 0031). [screens] lists every `screens/<id>.json`
 * in the order the admin shows them; [home] is the screen the app opens on.
 */
@Serializable
data class TemplateManifest(
    val name: String,
    val description: String,
    val home: String,
    val screens: List<String>,
    /** `{{secret.NAME}}` the screens use: shown as a checklist ("set COMPOSECAST_SECRET_NAME"). */
    val secrets: List<RequiredSecret> = emptyList(),
    /** Test data: a data-source URL containing [Fixture.match] is answered with [Fixture.file]. */
    val fixtures: List<Fixture> = emptyList(),
)

@Serializable
data class RequiredSecret(val name: String, val why: String)

@Serializable
data class Fixture(val match: String, val file: String)

/** A whole app: screens, brand and navigation between them (one bundle). */
class AppTemplate(
    /** The bundle's directory name, e.g. `news`. */
    val id: String,
    val manifest: TemplateManifest,
    /** In [TemplateManifest.screens] order. */
    val screens: List<ScreenDefinition>,
    val brand: BrandConfig?,
    private val fixtureFiles: Map<String, String> = emptyMap(),
) {
    /** The fixture body for a data-source request [url] (tests, offline previews), or null. */
    fun fixture(url: String): String? = manifest.fixtures.firstOrNull { it.match in url }?.let { fixtureFiles[it.file] }

    /**
     * Everything wrong with this bundle, each prefixed with its file: screen problems, a manifest
     * that does not match the files, the home screen, navigation to screens outside the bundle.
     */
    fun problems(): List<String> = buildList {
        val ids = screens.map { it.id }
        if (manifest.home !in ids) add("manifest.json: home '${manifest.home}' is not one of the screens")
        if (ids.toSet().size != ids.size) add("manifest.json: duplicate screen ids")
        screens.forEach { screen ->
            screen.problems().forEach { add("screens/${screen.id}.json: $it") }
            navigationTargets(screen).filter { it !in ids && !it.contains("{{") }.forEach {
                add("screens/${screen.id}.json: navigates to '$it', which is not in this template")
            }
        }
        brand?.problems()?.forEach { add("brand.json: $it") }
        manifest.fixtures.filter { it.file !in fixtureFiles }.forEach { add("manifest.json: missing ${it.file}") }
    }

    /**
     * The same app with every screen id (and every navigation to it) prefixed: `news` → `blog-news`,
     * for an admin that already has a screen with one of these ids.
     */
    fun withPrefix(prefix: String): AppTemplate {
        if (prefix.isEmpty()) return this
        val ids = screens.map { it.id }.toSet()
        fun rename(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> {
                val navigate = (element["type"] as? JsonPrimitive)?.content == "navigate"
                JsonObject(
                    element.mapValues { (key, value) ->
                        val target = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                        if (navigate && key == "screen" &&
                            target in ids
                        ) {
                            JsonPrimitive(prefix + target)
                        } else {
                            rename(value)
                        }
                    },
                )
            }
            is JsonArray -> JsonArray(element.map(::rename))
            else -> element
        }
        val renamed = screens.map { screen ->
            val json = rename(Json.parseToJsonElement(ScreenJson.encode(screen))) as JsonObject
            ScreenJson.decode(
                JsonObject(json + ("id" to JsonPrimitive(prefix + screen.id))).toString(),
                "${prefix}${screen.id}",
            )
        }
        return AppTemplate(
            id,
            manifest.copy(home = prefix + manifest.home, screens = manifest.screens.map { prefix + it }),
            renamed,
            brand,
            fixtureFiles,
        )
    }

    private fun navigationTargets(screen: ScreenDefinition): List<String> {
        fun walk(element: JsonElement): List<String> = when (element) {
            is JsonObject -> {
                val own = if ((element["type"] as? JsonPrimitive)?.content == "navigate") {
                    listOfNotNull((element["screen"] as? JsonPrimitive)?.content)
                } else {
                    emptyList()
                }
                own + element.values.flatMap(::walk)
            }
            is JsonArray -> element.flatMap(::walk)
            else -> emptyList()
        }
        return walk(Json.parseToJsonElement(ScreenJson.encode(screen)))
    }
}

/** The templates bundled in this module, from `templates/index.json` (a jar cannot be listed). */
object AppTemplates {
    private const val ROOT = "templates"
    private val json = Json { ignoreUnknownKeys = false }

    val all: List<AppTemplate> by lazy {
        json.decodeFromString<List<String>>(read("$ROOT/index.json")).map(::load)
    }

    operator fun get(id: String): AppTemplate? = all.firstOrNull { it.id == id }

    private fun load(id: String): AppTemplate {
        val dir = "$ROOT/$id"
        val manifest = json.decodeFromString(TemplateManifest.serializer(), read("$dir/manifest.json"))
        val screens = manifest.screens.map { ScreenJson.decode(read("$dir/screens/$it.json"), "$dir/screens/$it.json") }
        val brand = readOrNull("$dir/brand.json")?.let { json.decodeFromString(BrandConfig.serializer(), it) }
        val fixtures = manifest.fixtures.mapNotNull { f -> readOrNull("$dir/${f.file}")?.let { f.file to it } }.toMap()
        return AppTemplate(id, manifest, screens, brand, fixtures)
    }

    private fun readOrNull(path: String): String? =
        AppTemplates::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun read(path: String): String = requireNotNull(readOrNull(path)) { "missing bundled template file $path" }
}
