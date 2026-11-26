package io.composecast.server.admin

import io.composecast.protocol.Component
import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentParam
import io.composecast.protocol.ComponentParamType
import io.composecast.protocol.DataSource
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject

/**
 * Property forms generated from the screen model's `SerialDescriptor`s (T-029 C2, ADR 0028): every
 * field of every component becomes an input without editor code. Component lists (`children`,
 * `content`, …) are not form fields: they are the tree. Values are read from and written to the
 * screen JSON; the caller validates the result with `ScreenJson.check`.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object FormSchema {
    sealed interface Kind {
        data object Text : Kind

        data class Number(val integer: Boolean) : Kind

        /** A non-null boolean (all default to false; false is left out). */
        data object Toggle : Kind

        /** An enum, or a nullable boolean (`true`/`false`/unset). Empty = unset. */
        data class Choice(val options: List<String>) : Kind

        /** A nested object (modifier, border, semantics, padding sides). */
        data class Group(val fields: List<Field>) : Kind

        /** A sealed type (actions): `<path>.type` picks the variant. */
        data class Variant(val variants: Map<String, List<Field>>) : Kind

        /** `Dimension`: a number (dp), `fill` or `wrap`. */
        data object Dimension : Kind

        /** Anything else (maps, lists, `RepeatItems`): a JSON value; plain text is a string. */
        data object JsonValue : Kind
    }

    /** [help] and [input] override the defaults by key (`VisualPages.HELP`, input type from the kind). */
    data class Field(
        val key: String,
        val path: String,
        val label: String,
        val kind: Kind,
        val optional: Boolean,
        val help: String? = null,
        val input: String? = null,
    )

    private const val COMPONENT = "io.composecast.protocol.Component"

    /** Read values with defaults written out, so forms show what the component really uses. */
    private val withDefaults = Json { encodeDefaults = true }

    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private val componentDescriptor = Component.serializer().descriptor

    /** Subtype descriptors of [Component] by JSON `type`. */
    val componentTypes: Map<String, SerialDescriptor> = componentDescriptor.getElementDescriptor(1).let { subtypes ->
        (0 until subtypes.elementsCount).associate { subtypes.getElementName(it) to subtypes.getElementDescriptor(it) }
    }

    /** Fields of a component of [type] (null for an unknown type). */
    fun componentFields(type: String): List<Field>? = componentTypes[type]?.let { fields(it, "") }

    /**
     * T-049: the form of a `custom` use of [definition]: `component` picks one of [componentIds] and
     * `args` is one input per param (typed hint, description, default) instead of a JSON object.
     * Args are strings in the model, so every input is text (bindings like `{{item.title}}` work).
     */
    fun customFields(definition: ComponentDefinition, componentIds: List<String>): List<Field> =
        componentFields("custom")!!.map { field ->
            when (field.key) {
                "component" -> field.copy(kind = Kind.Choice(componentIds), help = "the custom component to show")
                "args" -> Field(
                    key = "args",
                    path = "args",
                    label = "Arguments",
                    kind = Kind.Group(
                        definition.params.map { (name, param) ->
                            Field(
                                key = name,
                                path = "args.$name",
                                label = label(name),
                                kind = Kind.Text,
                                optional = param.default != null,
                                help = paramHelp(param),
                                input = if (param.type == ComponentParamType.Color) "color" else "text",
                            )
                        },
                    ),
                    optional = definition.params.values.all { it.default != null },
                    help = definition.description.ifBlank { null },
                )
                else -> field
            }
        }

    private fun paramHelp(param: ComponentParam): String = listOfNotNull(
        param.description.ifBlank { null },
        when (param.type) {
            ComponentParamType.Text -> null
            ComponentParamType.Color -> "#RRGGBB or #AARRGGBB"
            ComponentParamType.Number -> "a number"
            ComponentParamType.Action -> "an action, e.g. navigate:detail?id=1 or open_url:https://…"
        },
        param.default?.let { "empty = $it" } ?: "required",
    ).joinToString(" · ")

    /** Screen-level fields: everything but id (fixed), component lists, and data/values (tree nodes). */
    val screenFields: List<Field> =
        fields(ScreenDefinition.serializer().descriptor, "", skip = setOf("id", "data", "values"))

    /** Fields of one data source (T-031 W3): the sources are nodes of the tree (`data.<name>`), not a JSON field. */
    val sourceFields: List<Field> = fields(DataSource.serializer().descriptor, "")

    /** A data source's JSON with its defaults written out, for [values]. */
    fun sourceWithDefaults(source: JsonObject): JsonObject = withDefaults.encodeToJsonElement(
        DataSource.serializer(),
        Json.decodeFromJsonElement(DataSource.serializer(), source),
    ) as JsonObject

    /** Fields of one on-device screen value (`values.<name>` tree node): default, min, max, animate (mode/easing dropdowns). */
    val valueFields: List<Field> = fields(ScreenValue.serializer().descriptor, "")

    /** A screen value's JSON with its defaults written out (keeping `animate` absent when unset). */
    fun valueWithDefaults(value: JsonObject): JsonObject {
        val decoded = Json.decodeFromJsonElement(ScreenValue.serializer(), value)
        val encoded = withDefaults.encodeToJsonElement(ScreenValue.serializer(), decoded) as JsonObject
        return if (decoded.animate == null) JsonObject(encoded - "animate") else encoded
    }

    /** Component-list field names of [descriptor] (`children`, `content`, `template`, `empty`, `components`, `error`). */
    fun componentLists(descriptor: SerialDescriptor): List<String> =
        (0 until descriptor.elementsCount).filter { isComponentList(descriptor.getElementDescriptor(it)) }
            .map(descriptor::getElementName)

    private fun isComponentList(d: SerialDescriptor) =
        d.kind == StructureKind.LIST && d.getElementDescriptor(0).serialName.removeSuffix("?") == COMPONENT

    private fun fields(descriptor: SerialDescriptor, prefix: String, skip: Set<String> = emptySet()): List<Field> =
        (0 until descriptor.elementsCount).mapNotNull { i ->
            val key = descriptor.getElementName(i)
            val element = descriptor.getElementDescriptor(i)
            if (key in skip || isComponentList(element)) return@mapNotNull null
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            Field(key, path, label(key), kind(element, path), descriptor.isElementOptional(i) || element.isNullable)
        }

    private fun kind(d: SerialDescriptor, path: String): Kind = when {
        d.serialName.removeSuffix("?") == "Dimension" -> Kind.Dimension
        // Custom serializers over any JSON (RepeatItems) describe themselves as JsonElement (a sealed kind).
        d.serialName.removeSuffix("?") == "kotlinx.serialization.json.JsonElement" -> Kind.JsonValue
        d.kind == PrimitiveKind.STRING -> Kind.Text
        d.kind == PrimitiveKind.INT || d.kind == PrimitiveKind.LONG || d.kind == PrimitiveKind.SHORT ||
            d.kind == PrimitiveKind.BYTE -> Kind.Number(integer = true)
        d.kind == PrimitiveKind.FLOAT || d.kind == PrimitiveKind.DOUBLE -> Kind.Number(integer = false)
        d.kind == PrimitiveKind.BOOLEAN -> if (d.isNullable) Kind.Choice(listOf("true", "false")) else Kind.Toggle
        d.kind == SerialKind.ENUM -> Kind.Choice((0 until d.elementsCount).map(d::getElementName))
        d.kind == StructureKind.CLASS || d.kind == StructureKind.OBJECT -> Kind.Group(fields(d, path))
        d.kind == PolymorphicKind.SEALED -> d.getElementDescriptor(1).let { subtypes ->
            Kind.Variant(
                (0 until subtypes.elementsCount).associate {
                    subtypes.getElementName(it) to
                        fields(subtypes.getElementDescriptor(it), path)
                },
            )
        }
        else -> Kind.JsonValue
    }

    /** `fontWeight` → "Font weight". */
    private fun label(key: String) =
        key.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar(Char::uppercase)

    /** The node (component or screen object) with its defaults written out, for [values]. */
    fun withDefaults(component: Component): JsonObject =
        withDefaults.encodeToJsonElement(Component.serializer(), component) as JsonObject

    /** Form values by field path ("on" for a set toggle). */
    fun values(fields: List<Field>, obj: JsonObject?): Map<String, String> = buildMap { collect(fields, obj, this) }

    private fun collect(fields: List<Field>, obj: JsonObject?, out: MutableMap<String, String>) {
        for (field in fields) {
            val value = obj?.get(field.key)?.takeUnless { it is JsonNull } ?: continue
            when (val kind = field.kind) {
                Kind.Toggle -> if ((value as? JsonPrimitive)?.booleanOrNull == true) out[field.path] = "on"
                is Kind.Group -> when (value) {
                    is JsonObject -> collect(kind.fields, value, out)
                    // Padding written as one number = all sides.
                    is JsonPrimitive -> kind.fields.forEach { out[it.path] = value.content }
                    else -> Unit
                }
                is Kind.Variant -> (value as? JsonObject)?.let { variant ->
                    val type = (variant["type"] as? JsonPrimitive)?.content ?: return@let
                    out["${field.path}.type"] = type
                    collect(kind.variants[type].orEmpty(), variant, out)
                }
                Kind.JsonValue -> out[field.path] =
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?: pretty.encodeToString(JsonElement.serializer(), value)
                else -> out[field.path] = (value as? JsonPrimitive)?.content ?: value.toString()
            }
        }
    }

    /** Whether any input under [fields] has a value (a set toggle, a chosen variant, non-blank text). */
    fun filledIn(fields: List<Field>, param: (String) -> String?): Boolean = fields.any { field ->
        when (val kind = field.kind) {
            is Kind.Group -> filledIn(kind.fields, param)
            is Kind.Variant -> !param("${field.path}.type").isNullOrBlank()
            else -> !param(field.path).isNullOrBlank()
        }
    }

    /**
     * The JSON object the form describes; unset optional fields are left out. Problems (not a number,
     * a required field empty) are added to [problems] with the field's path.
     */
    fun parse(fields: List<Field>, param: (String) -> String?, problems: MutableList<String>): JsonObject =
        buildJsonObject {
            for (field in fields) {
                val raw = param(field.path)
                val trimmed = raw?.trim().orEmpty()
                val value: JsonElement? = when (val kind = field.kind) {
                    Kind.Text -> if (raw.isNullOrEmpty() && field.optional) null else JsonPrimitive(raw.orEmpty())
                    is Kind.Number -> when {
                        trimmed.isEmpty() -> null.also { if (!field.optional) problems += "${field.path}: required" }
                        kind.integer -> trimmed.toLongOrNull()?.let(::JsonPrimitive)
                            ?: null.also { problems += "${field.path}: expected a whole number, was '$trimmed'" }
                        else -> trimmed.toDoubleOrNull()?.let(::JsonPrimitive)
                            ?: null.also { problems += "${field.path}: expected a number, was '$trimmed'" }
                    }
                    Kind.Toggle -> if (raw == "on") JsonPrimitive(true) else null
                    is Kind.Choice -> when {
                        trimmed.isEmpty() -> null
                        trimmed == "true" || trimmed == "false" -> JsonPrimitive(trimmed.toBoolean())
                        else -> JsonPrimitive(trimmed)
                    }
                    Kind.Dimension -> if (trimmed.isEmpty()) {
                        null
                    } else {
                        trimmed.toIntOrNull()?.let(::JsonPrimitive)
                            ?: JsonPrimitive(trimmed)
                    }
                    Kind.JsonValue -> if (trimmed.isEmpty()) {
                        null
                    } else {
                        runCatching {
                            Json.parseToJsonElement(trimmed)
                        }.getOrElse { JsonPrimitive(trimmed) }
                    }
                    // An optional group nobody filled in stays absent (its required fields must not create it).
                    is Kind.Group -> if (field.optional &&
                        !filledIn(kind.fields, param)
                    ) {
                        null
                    } else {
                        parse(kind.fields, param, problems)
                    }
                    is Kind.Variant -> param("${field.path}.type")?.takeIf { it.isNotEmpty() }?.let { type ->
                        val variant =
                            kind.variants[type]
                                ?: return@let null.also { problems += "${field.path}.type: unknown '$type'" }
                        JsonObject(mapOf("type" to JsonPrimitive(type)) + parse(variant, param, problems))
                    }
                }
                if (value != null) put(field.key, value)
            }
        }
}
