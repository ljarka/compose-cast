package io.composecast.server.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The visual editor's palette and structural edits (T-029 C3): add, delete, duplicate, move up/down,
 * into the previous container, out of the parent, wrap in a container. All work on the screen JSON
 * by node path ([ScreenTree]); the caller checks the result like any edit and saves it as the draft.
 */
internal object Palette {
    /**
     * The smallest useful valid component of each type; `PaletteTest` checks one exists for every
     * type. `repeat` needs a data source: [starter] points it at the screen's first one.
     */
    val starters: Map<String, JsonObject> = mapOf(
        "text" to """{"text": "New text"}""",
        "image" to """{"url": "https://picsum.photos/seed/compose-cast/600/300"}""",
        "icon" to """{"url": "https://picsum.photos/seed/compose-cast/96/96"}""",
        "button" to """{"label": "Button", "action": {"type": "navigate", "screen": "main"}}""",
        "divider" to """{}""",
        "spacer" to """{"height": 16}""",
        "column" to """{"children": []}""",
        "row" to """{"children": []}""",
        "box" to """{"children": []}""",
        "card" to """{"children": []}""",
        "grid" to """{"columns": 2, "children": []}""",
        "flow" to """{"children": []}""",
        "fitBox" to """{"children": []}""",
        "expandable" to """{"title": "Section", "content": [{"type": "text", "text": "Shown when opened"}]}""",
        "repeat" to """{"items": "items", "template": [{"type": "text", "text": "{{item}}"}]}""",
    ).mapValues { (type, json) ->
        JsonObject(mapOf("type" to JsonPrimitive(type)) + Json.parseToJsonElement(json) as JsonObject)
    }

    /** Palette sections for the page, in this order. */
    val groups: List<Pair<String, List<String>>> = listOf(
        "Content" to listOf("text", "image", "icon", "button", "divider", "spacer"),
        "Layout" to listOf("column", "row", "box", "card", "grid", "flow", "fitBox", "expandable"),
        "Data" to listOf("repeat"),
    )

    /** Types that hold components; [wrap] puts the node into the first list. */
    val containers: List<String> = FormSchema.componentTypes.filter {
        FormSchema.componentLists(it.value).isNotEmpty()
    }.keys.toList()

    /** A structural edit's result: the new screen and the node to select afterwards. */
    data class Result(val screen: JsonObject, val select: String)

    /** Where a component path lives: `components.1.children` + index 2. Null for the screen or a list path. */
    fun location(path: String): Pair<String, Int>? {
        val index = path.substringAfterLast('.', "").toIntOrNull() ?: return null
        return path.substringBeforeLast('.') to index
    }

    /** First component list of the container at [path] (`children`, `content`, `template`), or null. */
    fun firstList(screen: JsonObject, path: String): String? {
        val type =
            ((ScreenTree.get(screen, path) as? JsonObject)?.get("type") as? JsonPrimitive)?.content ?: return null
        return FormSchema.componentTypes[type]?.let(FormSchema::componentLists)?.firstOrNull()
    }

    private fun items(screen: JsonObject, list: String): List<JsonElement> =
        (ScreenTree.get(screen, list) as? JsonArray).orEmpty()

    private fun withItems(screen: JsonObject, list: String, items: List<JsonElement>) =
        ScreenTree.set(screen, list, JsonArray(items))

    /** The starter of [type] for [screen] (a repeat lists the screen's first data source). */
    fun starter(screen: JsonObject, type: String): JsonObject {
        val starter = requireNotNull(starters[type]) { "unknown component type '$type'" }
        if (type != "repeat") return starter
        val source = requireNotNull((screen["data"] as? JsonObject)?.keys?.firstOrNull()) {
            "a repeat lists data from an API: add a data source first (select Screen, field Data)"
        }
        return JsonObject(starter + ("items" to JsonPrimitive(source)))
    }

    /** Inserts a new [type] into [list] at [index] (clamped; null = at the end). */
    fun add(screen: JsonObject, list: String, index: Int?, type: String): Result {
        val starter = starter(screen, type)
        val current = items(screen, list)
        val at = (index ?: current.size).coerceIn(0, current.size)
        return Result(withItems(screen, list, current.take(at) + starter + current.drop(at)), "$list.$at")
    }

    fun delete(screen: JsonObject, path: String): Result {
        val (list, index) = requireNotNull(location(path)) { "not a component: $path" }
        val current = items(screen, list)
        val rest = current.filterIndexed { i, _ -> i != index }
        val select = when {
            rest.isEmpty() -> owner(list)
            index < rest.size -> "$list.$index"
            else -> "$list.${index - 1}"
        }
        return Result(withItems(screen, list, rest), select)
    }

    fun duplicate(screen: JsonObject, path: String): Result {
        val (list, index) = requireNotNull(location(path)) { "not a component: $path" }
        val current = items(screen, list)
        return Result(
            withItems(screen, list, current.take(index + 1) + current[index] + current.drop(index + 1)),
            "$list.${index + 1}",
        )
    }

    /** Swaps with the neighbour [by] (-1 up, +1 down); at the edge nothing changes. */
    fun move(screen: JsonObject, path: String, by: Int): Result {
        val (list, index) = requireNotNull(location(path)) { "not a component: $path" }
        val current = items(screen, list).toMutableList()
        val target = index + by
        if (target !in current.indices) return Result(screen, path)
        current[index] = current[target].also { current[target] = current[index] }
        return Result(withItems(screen, list, current), "$list.$target")
    }

    /** Moves the node to the end of the previous sibling, which must be a container. */
    fun intoPrevious(screen: JsonObject, path: String): Result {
        val (list, index) = requireNotNull(location(path)) { "not a component: $path" }
        require(index > 0) { "there is no component above to move into" }
        val previous = "$list.${index - 1}"
        val target = requireNotNull(firstList(screen, previous)) { "the component above holds no components" }
        val node = items(screen, list)[index]
        val removed = withItems(screen, list, items(screen, list).filterIndexed { i, _ -> i != index })
        val into = "$previous.$target"
        val inside = items(removed, into)
        return Result(withItems(removed, into, inside + node), "$into.${inside.size}")
    }

    /** Moves the node out of its container, right after the container. */
    fun outOfParent(screen: JsonObject, path: String): Result {
        val (list, index) = requireNotNull(location(path)) { "not a component: $path" }
        val parent = owner(list)
        val (parentList, parentIndex) = requireNotNull(location(parent)) { "already at the top level" }
        val node = items(screen, list)[index]
        val removed = withItems(screen, list, items(screen, list).filterIndexed { i, _ -> i != index })
        val siblings = items(removed, parentList)
        val at = parentIndex + 1
        return Result(withItems(removed, parentList, siblings.take(at) + node + siblings.drop(at)), "$parentList.$at")
    }

    /** Replaces the node with a new [type] container holding it. */
    fun wrap(screen: JsonObject, path: String, type: String): Result {
        requireNotNull(location(path)) { "not a component: $path" }
        val starter = requireNotNull(starters[type]?.takeIf { type in containers }) { "'$type' cannot hold components" }
        val list = FormSchema.componentLists(FormSchema.componentTypes.getValue(type)).first()
        val node = ScreenTree.get(screen, path)!!
        return Result(ScreenTree.set(screen, path, JsonObject(starter + (list to JsonArray(listOf(node))))), path)
    }

    /** The component that owns a list path (`components.1.children` → `components.1`), `""` for the screen's lists. */
    private fun owner(list: String) = if ('.' in list) list.substringBeforeLast('.') else ""
}
