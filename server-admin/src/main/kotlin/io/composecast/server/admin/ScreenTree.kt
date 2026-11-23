package io.composecast.server.admin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The component tree of a screen's JSON (T-029 C2). A node path is `""` (the screen) or
 * `components.0.children.2` (list name, index, …). Works on JSON so nothing is lost or reordered.
 */
internal object ScreenTree {
    /** One line of the outline: a list heading ([type] null) or a component. */
    data class Row(val path: String, val depth: Int, val type: String?, val label: String)

    /** Top-level component lists of a screen. */
    private val SCREEN_LISTS = listOf("components", "error")

    fun rows(screen: JsonObject): List<Row> = buildList {
        // T-031 W3: data sources first (what the components bind to), each one a node `data.<name>`.
        add(Row("data", 0, null, "Data sources"))
        (screen["data"] as? JsonObject)?.forEach { (name, source) ->
            add(
                Row(
                    "data.$name",
                    1,
                    "source",
                    "$name · ${((source as? JsonObject)?.get("url") as? JsonPrimitive)?.content.orEmpty()}".take(48),
                ),
            )
        }
        for (list in SCREEN_LISTS) {
            val items = screen[list] as? JsonArray
            // The error list matters only with data sources: shown (and fillable) once there are some.
            if (list == "error" && items.isNullOrEmpty() && (screen["data"] as? JsonObject).isNullOrEmpty()) continue
            add(Row(list, 0, null, if (list == "error") "Shown when a data source fails" else "Components"))
            items?.forEachIndexed { i, item -> addNode(this, "$list.$i", item, 1) }
        }
    }

    private fun addNode(rows: MutableList<Row>, path: String, node: JsonElement, depth: Int) {
        val obj = node as? JsonObject ?: return
        val type = (obj["type"] as? JsonPrimitive)?.content ?: "?"
        rows += Row(path, depth, type, summary(obj))
        val lists = FormSchema.componentTypes[type]?.let(FormSchema::componentLists).orEmpty()
        for (list in lists) {
            val items = obj[list] as? JsonArray
            // Components with several lists (repeat: template + empty) name each one, even an
            // absent one, so it can be selected and filled.
            val childDepth = if (lists.size > 1) depth + 2 else depth + 1
            if (lists.size > 1) rows += Row("$path.$list", depth + 1, null, list)
            items?.forEachIndexed { i, item -> addNode(rows, "$path.$list.$i", item, childDepth) }
        }
    }

    /** What a component shows, for the outline: its text, label, title or URL. */
    private fun summary(obj: JsonObject): String {
        val text = listOf("text", "label", "title", "url", "items").firstNotNullOfOrNull { key ->
            (obj[key] as? JsonPrimitive)?.content
        }.orEmpty()
        return if (text.length > 40) text.take(39) + "…" else text
    }

    /** The element at [path], or null when it does not exist. */
    fun get(root: JsonObject, path: String): JsonElement? = segments(path).fold(root as JsonElement?) { node, segment ->
        when (node) {
            is JsonObject -> node[segment]
            is JsonArray -> segment.toIntOrNull()?.let(node::getOrNull)
            else -> null
        }
    }

    /** [root] with the element at [path] replaced by [value] (the path must exist). */
    fun set(root: JsonObject, path: String, value: JsonElement): JsonObject =
        replace(root, segments(path), value) as JsonObject

    private fun replace(node: JsonElement, segments: List<String>, value: JsonElement): JsonElement {
        if (segments.isEmpty()) return value
        val head = segments.first()
        val rest = segments.drop(1)
        return when (node) {
            // The last key may be new (a component list that is still absent, e.g. `empty`).
            is JsonObject -> JsonObject(
                node + (head to if (rest.isEmpty()) value else replace(node.getValue(head), rest, value)),
            )
            is JsonArray -> {
                val index = head.toInt()
                JsonArray(node.mapIndexed { i, item -> if (i == index) replace(item, rest, value) else item })
            }
            else -> error("no element at '$head'")
        }
    }

    private fun segments(path: String) = if (path.isEmpty()) emptyList() else path.split('.')
}
