package io.composecast.server.admin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Template bindings (`{{name.path | filter}}`) in screen JSON, for editor refactorings (T-029 C3). */
internal object Bindings {
    /** [element] with every binding rooted at [old] rooted at [new] instead (`{{old.x}}` → `{{new.x}}`). */
    fun rename(element: JsonElement, old: String, new: String): JsonElement {
        val root = Regex("""(\{\{\s*)${Regex.escape(old)}(?=[\s.|}])""")
        fun walk(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.mapValues { walk(it.value) })
            is JsonArray -> JsonArray(e.map(::walk))
            is JsonPrimitive -> if (e.isString) {
                JsonPrimitive(
                    e.content.replace(root) {
                        "${it.groupValues[1]}$new"
                    },
                )
            } else {
                e
            }
        }
        return walk(element)
    }
}
