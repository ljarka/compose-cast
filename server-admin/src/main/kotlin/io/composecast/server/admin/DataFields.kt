package io.composecast.server.admin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The field picker of the visual editor (T-031 W3): every binding usable at a node, with a sample
 * value from the real data. The screen's sources are probed ([ApiProbe]) with the params' defaults;
 * inside a `repeat` its alias' fields come first (`{{item.name}}`).
 */
internal class DataFields(private val probe: ApiProbe) {
    /** A binding to insert (`{{shows.name}}`) and what it holds now. */
    data class Field(val binding: String, val sample: String)

    /** A group of fields: where they come from, or why there are none. */
    data class Group(val title: String, val fields: List<Field>, val error: String? = null)

    fun groups(screen: JsonObject, node: String): List<Group> = buildList {
        val params = (screen["params"] as? JsonObject).orEmpty().mapValues { (_, spec) ->
            ((spec as? JsonObject)?.get("default") as? JsonPrimitive)?.content.orEmpty()
        }
        val sources = (screen["data"] as? JsonObject).orEmpty()
        val data = sources.mapValues { (_, source) -> fetch(source as JsonObject, params) }

        // Repeats around the node, nearest first: their alias is what the template binds to.
        for ((alias, items) in repeatsAround(screen, node)) {
            add(itemGroup(alias, items, data))
        }
        for ((name, result) in data) {
            add(
                result.fold(
                    onSuccess = { Group("Data source $name", flatten(name, it)) },
                    onFailure = { Group("Data source $name", emptyList(), it.message) },
                ),
            )
        }
        if (params.isNotEmpty()) add(Group("Screen parameters", params.map { (k, v) -> Field("{{params.$k}}", v) }))
    }

    /** `{{alias.…}}` of a repeat whose items are a path (`shows`, `src.results`) or a zip (`{"day": "…"}`). */
    private fun itemGroup(alias: String, items: JsonElement, data: Map<String, Result<JsonElement>>): Group {
        val title = "Each item of the repeat ({{$alias}})"
        if (items is JsonObject) {
            return Group(
                title,
                items.keys.map { Field("{{$alias.$it}}", "one value of ${(items[it] as? JsonPrimitive)?.content}") } +
                    index(),
            )
        }
        val path = (items as? JsonPrimitive)?.content ?: return Group(title, emptyList(), "the repeat has no items")
        val root = path.substringBefore('.')
        val json =
            data[root]?.getOrNull()
                ?: return Group(title, emptyList(), "'$root' is not a data source of this screen (or did not answer)")
        val list =
            at(json, path.substringAfter('.', "")) as? JsonArray
                ?: return Group(title, emptyList(), "'$path' is not a list")
        val first = list.firstOrNull() ?: return Group(title, emptyList(), "'$path' is empty right now")
        return Group(title, flatten(alias, first) + index())
    }

    private fun index() = Field("{{\$index}}", "0, 1, 2, …")

    private fun fetch(source: JsonObject, params: Map<String, String>): Result<JsonElement> {
        val url = substitute((source["url"] as? JsonPrimitive)?.content.orEmpty(), params)
        val headers = (source["headers"] as? JsonObject).orEmpty()
            .mapValues { (_, v) -> substitute((v as? JsonPrimitive)?.content.orEmpty(), params) }
        val result = probe.probe(url, headers)
        return result.json?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException(result.error ?: "no data"))
    }

    companion object {
        private const val MAX_FIELDS = 80
        private val PARAM = Regex("""\{\{\s*params\.([A-Za-z0-9_]+)\s*}}""")

        /** `{{params.x}}` → the param's default; secrets stay for the probe to resolve. */
        fun substitute(template: String, params: Map<String, String>) =
            PARAM.replace(template) { params[it.groupValues[1]].orEmpty() }

        /** Scalars and lists under [json] as `{{prefix.path}}`, objects three levels deep. */
        fun flatten(prefix: String, json: JsonElement, depth: Int = 0): List<Field> = buildList {
            when (json) {
                is JsonObject -> if (depth <= 3) {
                    json.forEach { (key, value) -> addAll(flatten("$prefix.$key", value, depth + 1)) }
                }
                is JsonArray -> add(Field("{{$prefix}}", "a list of ${json.size}"))
                is JsonNull -> Unit
                is JsonPrimitive -> add(Field("{{$prefix}}", json.content.take(60)))
            }
        }.take(MAX_FIELDS)

        /** The element at a dotted [path] (`""` = [json] itself). */
        fun at(json: JsonElement, path: String): JsonElement? = if (path.isEmpty()) {
            json
        } else {
            path.split('.').fold(json as JsonElement?) { e, key ->
                (e as? JsonObject)?.get(key)
            }
        }

        /** `(alias, items)` of every repeat whose template or empty list contains [node], nearest first. */
        fun repeatsAround(screen: JsonObject, node: String): List<Pair<String, JsonElement>> {
            val segments = node.split('.')
            return (segments.size - 1 downTo 1).mapNotNull { end ->
                val path = segments.take(end).joinToString(".")
                val repeat = (ScreenTree.get(screen, path) as? JsonObject)?.takeIf {
                    (it["type"] as? JsonPrimitive)?.content ==
                        "repeat"
                }
                if (repeat == null || segments[end] != "template") return@mapNotNull null
                val alias = (repeat["as"] as? JsonPrimitive)?.content ?: "item"
                repeat["items"]?.let { alias to it }
            }
        }
    }
}
