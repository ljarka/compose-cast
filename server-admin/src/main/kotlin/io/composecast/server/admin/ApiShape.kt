package io.composecast.server.admin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What an API response looks like, for the data source wizard (T-031): the lists of objects it
 * contains, the fields of a list's items with sample values, and a guess of each field's role.
 * Pure functions on the JSON; the heuristics are checked on fixture APIs (`ApiShapeTest`).
 */
internal object ApiShape {
    /** A list of objects at [path] (`""` = the response itself, `results`, `data.items`). */
    data class ItemList(val path: String, val size: Int, val fields: List<Field>)

    /** A field of the list's items: [path] inside an item (`name`, `image.medium`) and a sample. */
    data class Field(val path: String, val sample: String, val kind: Kind)

    enum class Kind { Text, LongText, Number, Bool, ImageUrl, Url, Date }

    /** What the wizard puts where; null = not found. */
    data class Roles(val id: String?, val title: String?, val subtitle: String?, val image: String?)

    private const val MAX_DEPTH = 4
    private val IMAGE_EXTENSION = Regex("""(?i)\.(jpe?g|png|webp|gif)(\?.*)?$""")
    private val IMAGE_NAME = Regex("""(?i)(image|thumbnail|thumb|poster|picture|photo|avatar|cover|logo)""")
    private val DATE = Regex("""^\d{4}-\d{2}-\d{2}([T ][\d:.]+(Z|[+-]\d{2}:?\d{2})?)?$""")

    /** Every list of objects in [json], the largest first. */
    fun lists(json: JsonElement): List<ItemList> =
        buildList { collect(json, "", 0, this) }.sortedByDescending { it.size }

    private fun collect(json: JsonElement, path: String, depth: Int, out: MutableList<ItemList>) {
        if (depth > MAX_DEPTH) return
        when (json) {
            is JsonArray -> {
                val objects = json.filterIsInstance<JsonObject>()
                if (objects.isNotEmpty() &&
                    objects.size * 2 >= json.size
                ) {
                    out += ItemList(path, json.size, fields(objects))
                }
            }
            is JsonObject -> json.forEach { (key, value) ->
                collect(
                    value,
                    if (path.isEmpty()) key else "$path.$key",
                    depth + 1,
                    out,
                )
            }
            else -> Unit
        }
    }

    /** Scalar fields of the items (nested objects one level deep), with the first non-empty sample. */
    private fun fields(items: List<JsonObject>): List<Field> {
        val samples = linkedMapOf<String, String>()
        fun visit(obj: JsonObject, prefix: String, depth: Int) {
            for ((key, value) in obj) {
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                when (value) {
                    is JsonObject -> if (depth < 1) visit(value, path, depth + 1)
                    is JsonPrimitive -> if (value !is JsonNull && value.content.isNotBlank() &&
                        path !in samples
                    ) {
                        samples[path] = value.content
                    }
                    else -> Unit // arrays: not a single value to show
                }
            }
        }
        items.take(20).forEach { visit(it, "", 0) }
        return samples.map { (path, sample) -> Field(path, sample, kind(path, sample)) }
    }

    private fun kind(path: String, sample: String): Kind = when {
        sample.startsWith("http://") || sample.startsWith("https://") ->
            if (IMAGE_EXTENSION.containsMatchIn(sample) || IMAGE_NAME.containsMatchIn(path)) Kind.ImageUrl else Kind.Url
        sample == "true" || sample == "false" -> Kind.Bool
        sample.toDoubleOrNull() != null -> Kind.Number
        DATE.matches(sample) -> Kind.Date
        sample.length > 80 || '<' in sample -> Kind.LongText
        else -> Kind.Text
    }

    private val ID_NAMES = listOf("id", "slug", "uuid", "key", "code")
    private val TITLE_NAMES = listOf("name", "title", "headline", "label", "full_name", "fullName")
    private val SUBTITLE_NAMES =
        listOf("subtitle", "summary", "description", "overview", "excerpt", "body", "species", "status", "type")

    /** The guessed roles of [fields]: by name first, then by kind. */
    fun roles(fields: List<Field>): Roles {
        fun named(names: List<String>, accept: (Field) -> Boolean) = names.firstNotNullOfOrNull { name ->
            fields.firstOrNull {
                it.path.equals(name, ignoreCase = true) &&
                    accept(it)
            }
        }
        val id = named(ID_NAMES) { it.kind == Kind.Number || it.kind == Kind.Text }
        val title = named(TITLE_NAMES) { it.kind == Kind.Text || it.kind == Kind.LongText }
            ?: fields.firstOrNull { it.kind == Kind.Text && it != id }
        val subtitle = named(SUBTITLE_NAMES) { (it.kind == Kind.Text || it.kind == Kind.LongText) && it != title }
            ?: fields.firstOrNull { it.kind == Kind.LongText && it != title }
        // A medium-sized variant beats the original (smaller documents: images are embedded, ADR 0003).
        val images = fields.filter { it.kind == Kind.ImageUrl }
        val image =
            images.firstOrNull { it.path.endsWith("medium") }
                ?: images.firstOrNull { IMAGE_NAME.containsMatchIn(it.path) }
                ?: images.firstOrNull()
        return Roles(id?.path, title?.path, subtitle?.path, image?.path)
    }
}
