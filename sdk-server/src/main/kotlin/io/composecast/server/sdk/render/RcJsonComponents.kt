package io.composecast.server.sdk.render

import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.json.RcJsonBridge
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.Component
import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentParamType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.logging.Logger

/**
 * Custom components (ADR 0033, T-049): a [ComponentDefinition]'s raw RC JSON body, checked against
 * a whitelist, with arguments substituted, parsed into the screen's own writer through
 * [RcJsonBridge]. Nothing in a body can break the rest of a screen: a body that does not parse is
 * left out (with a warning) instead of half-written.
 */
internal object RcJsonComponents {
    private val log = Logger.getLogger(RcJsonComponents::class.java.name)

    const val MAX_BODY_CHARS = 64 * 1024
    const val MAX_ELEMENTS = 1000
    const val MAX_DEPTH = 32

    /** RC JSON component types a body may use (lower case, as the parser matches them). */
    val COMPONENT_TYPES = setOf(
        "box", "row", "column", "text", "canvas", "spacer", "flow", "fitbox", "collapsiblerow", "collapsiblecolumn",
    )

    /** Canvas commands a body may use: drawing, paint and transforms. No bitmaps, shaders, sound, particles. */
    val CANVAS_COMMANDS = setOf(
        "paint", "setcolor", "setstyle", "setstrokewidth",
        "drawrect", "drawline", "drawpath", "drawcircle", "drawoval", "drawroundrect", "drawarc", "drawsector",
        "drawtextanchored", "drawtextonpath", "drawtextoncircle", "drawtweenpath",
        "save", "restore", "scale", "rotate", "translate", "skew", "cliprect", "clippath", "rem",
    )

    /** Keys inside a `paint` command's `ops`; `shader` only as the number 0 (reset). */
    val PAINT_OPS = setOf(
        "color", "alpha", "style", "strokewidth", "strokecap", "strokejoin", "textsize",
        "lineargradient", "radialgradient", "sweepgradient", "patheffect", "shader",
    )

    /** Modifiers a body may not use: they reference document-level operations. */
    val FORBIDDEN_MODIFIERS = setOf("include", "includemacro")

    /** Modifiers that carry actions: only `hostAction` with a name our `ActionCodec` decodes. */
    private val ACTION_MODIFIERS = setOf(
        "onclick",
        "multiclick",
        "ontouchdown",
        "touchdown",
        "ontouchup",
        "touchup",
        "ontouchcancel",
        "touchcancel",
    )

    /**
     * Whitelist and limit problems of [body] after substitution (paths like `body.children[0]`).
     * [substituted] = true checks action names too (before that they may be `{{params.x}}`).
     */
    fun bodyProblems(body: JsonObject, substituted: Boolean): List<String> = buildList {
        if (body.toString().length > MAX_BODY_CHARS) add("body: larger than $MAX_BODY_CHARS characters")
        var elements = 0
        fun action(a: JsonElement, at: String) {
            val obj = a as? JsonObject ?: run {
                add("$at: expected an action object")
                return
            }
            val type = obj.string("type")?.lowercase()
            if (type != "hostaction" && type != "hostnamedaction") {
                add("$at.type: only hostAction is allowed, was '${obj.string("type")}'")
                return
            }
            if (obj.containsKey("value") || obj.containsKey("actionId")) {
                add("$at: use only \"name\" (our action wire form)")
            }
            val name = obj.string("name") ?: run {
                add("$at.name: required")
                return
            }
            val pending = ComponentDefinition.placeholders(name).any()
            if ((substituted || !pending) && ActionCodec.decode(name) == null) {
                add("$at.name: '$name' is not an action (e.g. navigate:detail)")
            }
        }
        fun modifiers(list: JsonElement?, at: String) {
            val arr = list as? JsonArray ?: return
            arr.forEachIndexed { i, item ->
                val key = when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject -> item.keys.firstOrNull()
                    else -> null
                }?.lowercase() ?: return@forEachIndexed
                if (key in FORBIDDEN_MODIFIERS) add("$at[$i]: modifier '$key' is not allowed in a custom component")
                if (key in ACTION_MODIFIERS && item is JsonObject) {
                    val value = item.values.first()
                    val actions = if (key == "multiclick") (value as? JsonObject)?.get("actions") else value
                    when (actions) {
                        is JsonArray -> actions.forEachIndexed { j, a -> action(a, "$at[$i].$key[$j]") }
                        null -> Unit
                        else -> action(actions, "$at[$i].$key")
                    }
                }
            }
        }
        fun command(c: JsonElement, at: String) {
            elements++
            val obj = c as? JsonObject ?: run {
                add("$at: expected a command object")
                return
            }
            val (type, args) = shorthand(obj)
            if (type !in CANVAS_COMMANDS) {
                add("$at.type: canvas command '$type' is not allowed; allowed: ${CANVAS_COMMANDS.sorted()}")
                return
            }
            if (type == "paint") {
                (args["ops"] as? JsonArray)?.forEachIndexed { i, op ->
                    (op as? JsonObject)?.forEach { (k, v) ->
                        val key = k.lowercase()
                        when {
                            key !in PAINT_OPS -> add("$at.ops[$i].$k: paint op is not allowed")
                            key == "shader" && (v as? JsonPrimitive)?.contentOrNull != "0" ->
                                add("$at.ops[$i].shader: runtime shaders are not allowed (only 0 to reset)")
                        }
                    }
                }
            }
        }
        fun component(c: JsonElement, at: String, depth: Int) {
            elements++
            if (depth > MAX_DEPTH) {
                run {
                    add("$at: nested deeper than $MAX_DEPTH levels")
                    return
                }
            }
            val obj = c as? JsonObject ?: run {
                add("$at: expected a component object")
                return
            }
            val (type, fields) = shorthand(obj)
            if (type !in COMPONENT_TYPES) {
                val name = obj.string("type") ?: type
                add("$at.type: component '$name' is not allowed; allowed: ${COMPONENT_TYPES.sorted()}")
                return
            }
            modifiers(fields["modifiers"], "$at.modifiers")
            val children = fields["children"] as? JsonArray
            if (type == "canvas") {
                if (!children.isNullOrEmpty()) {
                    add(
                        "$at.children: a canvas cannot have children (the parser drops them); " +
                            "put the canvas and the components in a box",
                    )
                }
                (fields["commands"] as? JsonArray)?.forEachIndexed { i, cmd -> command(cmd, "$at.commands[$i]") }
            } else {
                children?.forEachIndexed { i, child -> component(child, "$at.children[$i]", depth + 1) }
            }
        }
        component(body, "body", 1)
        if (elements > MAX_ELEMENTS) add("body: $elements components and commands, at most $MAX_ELEMENTS")
    }

    /** The type (lower case) and fields of a component or command, resolving `{"text": {...}}` shorthands. */
    private fun shorthand(obj: JsonObject): Pair<String, JsonObject> {
        obj.string("type")?.let { return it.lowercase() to obj }
        if (obj.size == 1) {
            val (key, value) = obj.entries.first()
            return key.lowercase() to when (value) {
                is JsonObject -> value
                is JsonArray -> JsonObject(mapOf("children" to value, "commands" to value))
                else -> JsonObject(emptyMap())
            }
        }
        return "" to obj
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    /**
     * [body] with `{{params.x}}` ([args], typed by [definition]) and `{{brand.x}}` ([brand]) replaced.
     * A string that is exactly one number placeholder becomes a JSON number.
     */
    fun substitute(definition: ComponentDefinition, args: Map<String, String>, brand: Map<String, String>): JsonObject {
        fun value(root: String, name: String): String? = when (root) {
            "params" -> args[name]
            "brand" -> brand[name]
            else -> null
        }
        fun walk(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.mapValues { walk(it.value) })
            is JsonArray -> JsonArray(e.map(::walk))
            is JsonPrimitive -> if (!e.isString) {
                e
            } else {
                val whole = ComponentDefinition.wholePlaceholder(e.content)
                val number = whole?.takeIf { it.first == "params" }
                    ?.let { definition.params[it.second]?.type == ComponentParamType.Number }
                if (number == true) {
                    JsonPrimitive(value(whole.first, whole.second)?.trim()?.toFloatOrNull() ?: 0f)
                } else {
                    JsonPrimitive(ComponentDefinition.substitute(e.content, ::value))
                }
            }
        }
        return walk(definition.body) as JsonObject
    }

    /** Sample arguments for checking a definition: defaults, else a valid value of each type. */
    fun sampleArgs(definition: ComponentDefinition): Map<String, String> = definition.params.mapValues { (_, p) ->
        p.default ?: when (p.type) {
            ComponentParamType.Text -> "Text"
            ComponentParamType.Color -> "#FF808080"
            ComponentParamType.Number -> "1"
            ComponentParamType.Action -> "reload:"
        }
    }

    /**
     * Parses [body] into a throw-away writer from [scratch]: the parser's error (with its context
     * path) or null. Run before writing into a real document, which a failed parse would corrupt.
     */
    fun parseProblem(body: JsonObject, scratch: () -> RemoteComposeWriter): String? = try {
        val writer = scratch()
        val parser = RcJsonBridge.parser(writer)
        writer.root { RcJsonBridge.parseComponent(parser, body.toString()) }
        writer.encodeToByteArray()
        null
    } catch (e: Exception) {
        val message = (e.message ?: e.javaClass.simpleName).replace("\n", " ")
        if ("Unsupported command" in message) "$message (RC path data: absolute M L H C S Q A, a, Z only)" else message
    }

    /** Renders [node]: a box with our modifier around the substituted body, or nothing (with a warning). */
    fun render(scope: RcScope, env: RenderEnv, node: Component.Custom, slot: Slot) {
        val custom = env.custom
        val entry = custom.components[node.component]
        val skip = when {
            entry == null -> "unknown component"
            entry.broken != null -> "broken: ${entry.broken}"
            custom.apiLevel < entry.definition.minApiLevel ->
                "client RC API level ${custom.apiLevel} < ${entry.definition.minApiLevel}"
            else -> null
        }
        if (skip != null) return warn(node.component, skip)
        val definition = entry!!.definition
        val argProblems = definition.argProblems(node.args, "custom", bound = true)
        if (argProblems.isNotEmpty()) return warn(node.component, argProblems.joinToString("; "))
        val body = substitute(definition, definition.resolveArgs(node.args), custom.brand)
        val problems = bodyProblems(body, substituted = true) + listOfNotNull(parseProblem(body, custom.scratchWriter))
        if (problems.isNotEmpty()) return warn(node.component, problems.joinToString("; "))
        with(scope) {
            Box(modifier = boxModel(env, node.modifier, slot)) {
                RcJsonBridge.parseComponent(env.jsonParser(), body.toString())
            }
        }
    }

    private fun warn(id: String, why: String) = log.warning("Custom component '$id' not rendered: $why")
}

/** A definition as the renderer sees it: [broken] says why it cannot be used (ADR 0033 versioning). */
internal class CustomEntry(val definition: ComponentDefinition, val broken: String?)

/** Per-render custom component context. */
internal class CustomContext(
    val components: Map<String, CustomEntry>,
    /** `{{brand.<field>}}` values (hex colors, defaults applied). */
    val brand: Map<String, String>,
    val apiLevel: Int,
    val scratchWriter: () -> RemoteComposeWriter,
) {
    companion object {
        val EMPTY = CustomContext(emptyMap(), emptyMap(), Int.MAX_VALUE) { error("no scratch writer") }
    }
}
