package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A reusable component whose [body] is raw Remote Compose JSON (`RemoteComposeJsonParser` format,
 * ADR 0033). Screens use it by id with [Component.Custom]; the server substitutes the arguments
 * into the body (`{{params.<name>}}`, `{{brand.<field>}}`) and parses it into the screen's document.
 * Stored as `components/<id>.json` (files mode) or in the admin database.
 */
@Serializable
public data class ComponentDefinition(
    val id: String,
    val title: String = id,
    val description: String = "",
    /** Raised on every change; screens always use the current version. */
    val version: Int = 1,
    /** The Remote Compose release the body was last validated against (ADR 0033: versioning). */
    val rcVersion: String,
    /** Clients with an older RC document API level get nothing in the component's place. */
    val minApiLevel: Int = DEFAULT_MIN_API_LEVEL,
    val params: Map<String, ComponentParam> = emptyMap(),
    /**
     * One RC JSON component (`{"type": "box", "modifiers": [...], "children": [...]}`). With a
     * [design] it is compiled from it ([ComponentJson] replaces it on every decode).
     */
    val body: JsonObject = JsonObject(emptyMap()),
    /** Drawn in the component designer (T-057, ADR 0034): the source of [body]. */
    val design: ComponentDesign? = null,
) {
    /** Problems with this definition itself (not the RC grammar — the renderer checks that). */
    public fun problems(): List<String> = buildList {
        if (!ID.matches(id)) add("id: expected letters, digits, '-' or '_', was '$id'")
        if (version < 1) add("version: must be >= 1, was $version")
        if (rcVersion.isBlank()) add("rcVersion: must not be blank")
        if (minApiLevel < 1) add("minApiLevel: must be >= 1, was $minApiLevel")
        params.forEach { (name, param) ->
            if (!PARAM_NAME.matches(name)) add("params.$name: name must be an identifier")
            param.default?.let { d -> param.type.problem(d)?.let { add("params.$name.default: $it") } }
        }
        design?.let { addAll(it.problems()) }
        if ((body["type"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) {
            add("body.type: the body must be one RC JSON component with a \"type\"")
        }
        val used = references(body, "params")
        (used - params.keys).sorted().forEach { add("body: {{params.$it}} is not declared in params") }
        references(body, "brand").filterNot { it in BRAND_FIELDS }.sorted().forEach {
            add("body: {{brand.$it}} is unknown; use one of ${BRAND_FIELDS.sorted()}")
        }
    }

    /**
     * Problems with the arguments of one use ([at] is its path). Bound arguments (`{{…}}`) are
     * checked after binding, when [bound] is true.
     */
    public fun argProblems(args: Map<String, String>, at: String, bound: Boolean = false): List<String> = buildList {
        (args.keys - params.keys).sorted().forEach { add("$at.args.$it: component '$id' has no such param") }
        params.forEach { (name, param) ->
            val value = args[name]
            when {
                value == null -> if (param.default == null) add("$at.args.$name: required by component '$id'")
                !bound && Template.hasBindings(value) -> Unit
                else -> param.type.problem(value)?.let { add("$at.args.$name: $it") }
            }
        }
    }

    /** The values substituted for `{{params.x}}`: [args] over the defaults. */
    public fun resolveArgs(args: Map<String, String>): Map<String, String> =
        params.mapNotNull { (name, param) -> (args[name] ?: param.default)?.let { name to it } }.toMap()

    public companion object {
        public const val DEFAULT_MIN_API_LEVEL: Int = 8
        private val ID = Regex("[A-Za-z0-9_-]+")
        private val PARAM_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** `{{brand.<field>}}` in a body: the screen's brand colors (T-040 defaults apply). */
        public val BRAND_FIELDS: Set<String> = setOf(
            "primaryColor",
            "secondaryColor",
            "backgroundColor",
            "surfaceColor",
            "textColor",
            "outlineColor",
        )

        /** `{{<root>.<name>}}` placeholders in [body]. */
        private val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z]+)\.([A-Za-z_][A-Za-z0-9_]*)\s*}}""")

        public fun placeholders(text: String): Sequence<Pair<String, String>> =
            PLACEHOLDER.findAll(text).map { it.groupValues[1] to it.groupValues[2] }

        /** Replaces every placeholder in [text] with [value] of (root, name); unknown ones stay. */
        public fun substitute(text: String, value: (String, String) -> String?): String =
            PLACEHOLDER.replace(text) { m -> value(m.groupValues[1], m.groupValues[2]) ?: m.value }

        /** The single placeholder [text] consists of, if it is exactly one (typed substitution). */
        public fun wholePlaceholder(text: String): Pair<String, String>? =
            PLACEHOLDER.matchEntire(text.trim())?.let { it.groupValues[1] to it.groupValues[2] }

        private fun references(element: JsonElement, root: String): Set<String> = buildSet {
            fun walk(e: JsonElement) {
                when (e) {
                    is JsonObject -> e.forEach { (k, v) ->
                        placeholders(k).filter { it.first == root }.forEach { add(it.second) }
                        walk(v)
                    }
                    is JsonArray -> e.forEach(::walk)
                    is JsonPrimitive -> if (e.isString) {
                        placeholders(e.content).filter { it.first == root }.forEach { add(it.second) }
                    }
                }
            }
            walk(element)
        }
    }
}

/** A parameter of a [ComponentDefinition]: what a screen passes in `args`. */
@Serializable
public data class ComponentParam(
    val type: ComponentParamType = ComponentParamType.Text,
    /** Used when a screen does not pass the argument; without it the argument is required. */
    val default: String? = null,
    val description: String = "",
)

@Serializable
public enum class ComponentParamType {
    /** Any string (bindable). */
    @SerialName("text")
    Text,

    /** `#RRGGBB` or `#AARRGGBB`. */
    @SerialName("color")
    Color,

    /** A number; substituted as a JSON number when a value is exactly `{{params.x}}`. */
    @SerialName("number")
    Number,

    /** An action in wire form (`navigate:detail?id=1`), used as a `hostAction` name. */
    @SerialName("action")
    Action,
    ;

    /** Why [value] is not valid for this type, or null. */
    public fun problem(value: String): String? = when (this) {
        Text -> null
        Color -> colorProblem(value)
        Number -> if (value.trim().toFloatOrNull()?.isFinite() == true) null else "expected a number, was '$value'"
        Action -> {
            val action = ActionCodec.decode(value)
            when {
                action == null -> "expected an action in wire form (e.g. navigate:detail), was '$value'"
                value.length > ActionCodec.MAX_ENCODED_LENGTH ->
                    "action longer than ${ActionCodec.MAX_ENCODED_LENGTH} characters"
                else -> null
            }
        }
    }
}

/** The JSON format of [ComponentDefinition] (`components/<id>.json`): strict, defaults omitted. */
public object ComponentJson {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** A definition with a [ComponentDefinition.design] is stored without its compiled body. */
    public fun encode(definition: ComponentDefinition): String = json.encodeToString(
        ComponentDefinition.serializer(),
        if (definition.design != null) definition.copy(body = JsonObject(emptyMap())) else definition,
    )

    /** Decodes and checks [ComponentDefinition.problems]; errors start with [source]. */
    public fun decode(text: String, source: String): ComponentDefinition {
        val checked = check(text)
        require(checked.problems.isEmpty()) { "$source: ${checked.problems.joinToString("; ")}" }
        return checked.definition!!
    }

    public data class Checked(val definition: ComponentDefinition?, val problems: List<String>)

    /** Like [decode] without throwing (admin editor). */
    public fun check(text: String): Checked {
        val definition = try {
            json.decodeFromString(ComponentDefinition.serializer(), text)
        } catch (e: SerializationException) {
            return Checked(null, listOf(e.message ?: "invalid JSON"))
        } catch (e: IllegalArgumentException) {
            return Checked(null, listOf(e.message ?: "invalid value"))
        }
        return check(definition)
    }

    /** Compiles a [ComponentDefinition.design] into its body, then checks the definition. */
    public fun check(definition: ComponentDefinition): Checked {
        val compiled = definition.design?.let { definition.copy(body = it.toBody()) } ?: definition
        val problems = compiled.problems()
        return Checked(compiled.takeIf { problems.isEmpty() }, problems)
    }
}
