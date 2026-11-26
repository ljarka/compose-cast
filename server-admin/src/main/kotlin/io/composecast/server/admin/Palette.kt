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
        "gauge" to """{"value": "42", "label": "42%"}""",
        "sparkline" to """{"values": "3, 5, 4, 7, 6, 9"}""",
        "kpi" to """{"value": "1,284", "label": "Key figure"}""",
        "slider" to """{"controls": "value"}""",
        "custom" to """{"component": "my-component"}""",
    ).mapValues { (type, json) ->
        JsonObject(mapOf("type" to JsonPrimitive(type)) + Json.parseToJsonElement(json) as JsonObject)
    }

    /** Palette sections for the page, in this order. */
    val groups: List<Pair<String, List<String>>> = listOf(
        "Content" to listOf("text", "image", "icon", "button", "divider", "spacer"),
        "Layout" to listOf("column", "row", "box", "card", "grid", "flow", "fitBox", "expandable"),
        "Data" to listOf("repeat"),
        "Dashboard" to listOf("gauge", "sparkline", "kpi", "slider"),
    )

    /** Palette type of custom component [id] (T-049): `custom:<id>`. */
    const val CUSTOM_PREFIX = "custom:"

    /** A `custom` use of component [id] with [args] (T-049, ADR 0033). */
    fun customStarter(id: String, args: Map<String, String>): JsonObject = JsonObject(
        buildMap {
            put("type", JsonPrimitive("custom"))
            put("component", JsonPrimitive(id))
            if (args.isNotEmpty()) put("args", JsonObject(args.mapValues { JsonPrimitive(it.value) }))
        },
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

    /** The starter of [type] for [screen] (repeat → first data source; slider → first screen value). */
    fun starter(screen: JsonObject, type: String, custom: Map<String, Map<String, String>> = emptyMap()): JsonObject {
        if (type.startsWith(CUSTOM_PREFIX)) {
            val id = type.removePrefix(CUSTOM_PREFIX)
            return customStarter(id, requireNotNull(custom[id]) { "unknown component '$id': publish it first" })
        }
        val starter = requireNotNull(starters[type]) { "unknown component type '$type'" }
        if (type == "repeat") {
            val source = requireNotNull((screen["data"] as? JsonObject)?.keys?.firstOrNull()) {
                "a repeat lists data from an API: add a data source first (select Screen, field Data)"
            }
            return JsonObject(starter + ("items" to JsonPrimitive(source)))
        }
        if (type == "slider") {
            val valuesObj = (screen["values"] as? JsonObject).orEmpty()
            val controllable = valuesObj.entries.filter { (_, v) ->
                (v as? JsonObject)?.containsKey("animate") != true
            }.map { it.key }
            require(controllable.isNotEmpty()) {
                "a slider controls a non-animated screen value: add one first (select Screen → Screen values)"
            }
            val used = controlledValues(screen)
            val chosen = controllable.firstOrNull { it !in used } ?: controllable.first()
            return JsonObject(starter + ("controls" to JsonPrimitive(chosen)))
        }
        return starter
    }

    private fun controlledValues(element: JsonElement): Set<String> = buildSet {
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> {
                    if ((e["type"] as? JsonPrimitive)?.content == "slider") {
                        (e["controls"] as? JsonPrimitive)?.content?.let(::add)
                    }
                    e.values.forEach(::walk)
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
    }

    /** Inserts a new [type] into [list] at [index] (clamped; null = at the end). */
    fun add(
        screen: JsonObject,
        list: String,
        index: Int?,
        type: String,
        custom: Map<String, Map<String, String>> = emptyMap(),
    ): Result {
        val starter = starter(screen, type, custom)
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

    /** Moves component [fromPath] before, after or inside [targetPath] (drag-and-drop on the tree). */
    fun moveTo(screen: JsonObject, fromPath: String, targetPath: String, position: String): Result {
        val (fromList, fromIndex) = requireNotNull(location(fromPath)) { "not a component: $fromPath" }
        require(targetPath != fromPath && !targetPath.startsWith("$fromPath.")) {
            "cannot move a component into itself"
        }
        val node = items(screen, fromList)[fromIndex]
        val removed = withItems(screen, fromList, items(screen, fromList).filterIndexed { i, _ -> i != fromIndex })
        fun adjust(path: String): String {
            if (!path.startsWith("$fromList.")) return path
            val rest = path.removePrefix("$fromList.")
            val idx = rest.substringBefore('.').toIntOrNull() ?: return path
            if (idx <= fromIndex) return path
            val suffix = if ('.' in rest) "." + rest.substringAfter('.') else ""
            return "$fromList.${idx - 1}$suffix"
        }
        val dest = adjust(targetPath)
        if (position == "inside") {
            val targetList = when {
                dest.isEmpty() -> "components"
                location(dest) != null ->
                    "$dest." + requireNotNull(firstList(removed, dest)) { "cannot move inside '$dest'" }
                else -> {
                    require(
                        dest != "data" && !dest.startsWith("data.") &&
                            dest != "values" && !dest.startsWith("values."),
                    ) {
                        "cannot move into data sources or screen values"
                    }
                    dest
                }
            }
            val current = items(removed, targetList)
            return Result(withItems(removed, targetList, current + node), "$targetList.${current.size}")
        }
        require(position == "before" || position == "after") { "unknown drop position '$position'" }
        val (targetList, targetIndex) = requireNotNull(location(dest)) { "not a component: $targetPath" }
        val insertAt = if (position == "before") targetIndex else targetIndex + 1
        val current = items(removed, targetList)
        return Result(
            withItems(removed, targetList, current.take(insertAt) + node + current.drop(insertAt)),
            "$targetList.$insertAt",
        )
    }

    private val SOURCE_NAME = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")

    /** Adds the data source [name] with [url] (T-031 W3); its other fields have defaults. */
    fun addSource(screen: JsonObject, name: String, url: String): Result {
        require(SOURCE_NAME.matches(name)) { "a data source name uses letters, digits and _ (starting with a letter)" }
        require(url.isNotBlank()) { "a data source needs an address" }
        val sources = screen["data"] as? JsonObject ?: JsonObject(emptyMap())
        require(name !in sources) { "there is already a data source '$name'" }
        val added = JsonObject(sources + (name to JsonObject(mapOf("url" to JsonPrimitive(url.trim())))))
        return Result(JsonObject(screen + ("data" to added)), "data.$name")
    }

    /** Removes the data source at `data.<name>`; components that still bind to it make the screen invalid. */
    fun deleteSource(screen: JsonObject, path: String): Result {
        val name = path.removePrefix("data.")
        val sources = requireNotNull(screen["data"] as? JsonObject) { "no data sources" }
        require(name in sources) { "no data source '$name'" }
        return Result(JsonObject(screen + ("data" to JsonObject(sources - name))), "")
    }

    /** Adds an on-device screen value [name] (default, min, max); editable in its own form node `values.<name>`. */
    fun addValue(
        screen: JsonObject,
        name: String,
        default: String = "0",
        min: String = "0",
        max: String = "100",
    ): Result {
        require(SOURCE_NAME.matches(name)) { "a value name uses letters, digits and _ (starting with a letter)" }
        val existing = screen["values"] as? JsonObject ?: JsonObject(emptyMap())
        require(name !in existing) { "there is already a screen value '$name'" }
        val fields = linkedMapOf<String, JsonElement>()
        val trimmedDefault = default.trim().ifEmpty { "0" }
        if (trimmedDefault != "0") fields["default"] = JsonPrimitive(trimmedDefault)
        min.trim().toFloatOrNull()?.let { if (it != 0f) fields["min"] = JsonPrimitive(it) }
        max.trim().toFloatOrNull()?.let { if (it != 100f) fields["max"] = JsonPrimitive(it) }
        val added = JsonObject(existing + (name to JsonObject(fields)))
        return Result(JsonObject(screen + ("values" to added)), "values.$name")
    }

    /** Removes the screen value at `values.<name>`; components still referencing it make the screen invalid. */
    fun deleteValue(screen: JsonObject, path: String): Result {
        val name = path.removePrefix("values.")
        val existing = requireNotNull(screen["values"] as? JsonObject) { "no screen values" }
        require(name in existing) { "no screen value '$name'" }
        val rest = existing - name
        val updated = if (rest.isEmpty()) {
            JsonObject(screen - "values")
        } else {
            JsonObject(screen + ("values" to JsonObject(rest)))
        }
        return Result(updated, "")
    }

    /** The component that owns a list path (`components.1.children` → `components.1`), `""` for the screen's lists. */
    private fun owner(list: String) = if ('.' in list) list.substringBeforeLast('.') else ""
}
