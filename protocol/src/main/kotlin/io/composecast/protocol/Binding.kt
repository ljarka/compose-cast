package io.composecast.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Data bindings in the screen model (T-006, ADR 0008): `{{path | filter:arg}}` templates in string
 * fields, resolved on the server before rendering. Parsing and validation live here (the model is
 * the product, ADR 0015: the admin panel validates with the same code); evaluation against JSON
 * data lives in sdk-server (`Binder`).
 */

/** A screen parameter (query parameter of `/screens/{id}`); no [default] = required. */
@Serializable
data class ParamSpec(val default: String? = null)

/**
 * An HTTP GET returning JSON, fetched on every render (cached for [cacheSeconds]).
 * [url] and [headers] may use `{{params.x}}` and `{{secret.NAME}}` (env `COMPOSECAST_SECRET_NAME`).
 * A failing source shows the screen's `error` components (or HTTP 502) unless [optional].
 */
@Serializable
data class DataSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Int = 5000,
    val cacheSeconds: Int = 60,
    val optional: Boolean = false,
)

/**
 * What a `repeat` iterates over: a [Path] to an array, or [Zip] — named paths to parallel arrays,
 * combined by index into objects (`{"day": "w.daily.time", "max": "w.daily.max"}` → `{day, max}`).
 * JSON: a string or an object of strings.
 */
@Serializable(with = RepeatItemsSerializer::class)
sealed interface RepeatItems {
    data class Path(val path: String) : RepeatItems

    data class Zip(val arrays: Map<String, String>) : RepeatItems

    /** Every path with its JSON-like location suffix (`""` or `.name`). */
    fun paths(): List<Pair<String, String>> = when (this) {
        is Path -> listOf("" to path)
        is Zip -> arrays.map { (name, path) -> ".$name" to path }
    }
}

internal object RepeatItemsSerializer : KSerializer<RepeatItems> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: RepeatItems) {
        val element = when (value) {
            is RepeatItems.Path -> JsonPrimitive(value.path)
            is RepeatItems.Zip -> JsonObject(value.arrays.mapValues { JsonPrimitive(it.value) })
        }
        (encoder as JsonEncoder).encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): RepeatItems {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString -> RepeatItems.Path(element.content)
            element is JsonObject && element.values.all { it is JsonPrimitive && it.isString } ->
                RepeatItems.Zip(element.mapValues { (it.value as JsonPrimitive).content })
            else -> throw IllegalArgumentException(
                "repeat items must be a path string or an object of path strings, was $element",
            )
        }
    }
}

/** One step of a binding path after its root: `.key` or `[index]`. */
sealed interface PathStep {
    data class Key(val name: String) : PathStep

    data class Index(val index: Int) : PathStep
}

/** `name:arg:arg` after a `|`; args are unquoted strings (numbers and bare names kept as written). */
data class FilterCall(val name: String, val args: List<String>)

/** The filters templates may use, with their allowed argument counts. Implemented in sdk-server `Binder`. */
object Filters {
    val ARITY: Map<String, IntRange> = linkedMapOf(
        "round" to 0..1,
        "date" to 1..1,
        "lookup" to 1..1,
        "default" to 1..1,
        "upper" to 0..0,
        "lower" to 0..0,
    )
}

/** A string with `{{ … }}` expressions, e.g. `"{{w.temp | round}}°C"`. */
class Template private constructor(val parts: List<Part>) {
    sealed interface Part

    data class Literal(val text: String) : Part

    /** `root(.key|[n])* (| filter(:arg)*)*`; [text] is the source between the braces. */
    data class Expression(val root: String, val path: List<PathStep>, val filters: List<FilterCall>, val text: String) :
        Part

    val expressions: List<Expression> get() = parts.filterIsInstance<Expression>()

    companion object {
        /** Whether [text] contains a binding (then it must parse). `{{` cannot be escaped. */
        fun hasBindings(text: String): Boolean = "{{" in text

        /** @throws IllegalArgumentException describing the syntax error */
        fun parse(text: String): Template {
            val parts = mutableListOf<Part>()
            var i = 0
            while (i < text.length) {
                val open = text.indexOf("{{", i)
                if (open < 0) {
                    parts += Literal(text.substring(i))
                    break
                }
                if (open > i) parts += Literal(text.substring(i, open))
                val close = text.indexOf("}}", open + 2)
                require(close >= 0) { "unclosed '{{' in \"$text\"" }
                parts += parseExpression(text.substring(open + 2, close).trim())
                i = close + 2
            }
            return Template(parts)
        }

        /** A bare path without braces or filters, e.g. repeat items `weather.daily.time`. */
        fun parsePath(text: String): Expression {
            val expression = parseExpression(text.trim())
            require(expression.filters.isEmpty()) { "filters are not allowed in a path: \"$text\"" }
            return expression
        }

        private fun parseExpression(text: String): Expression {
            require(text.isNotEmpty()) { "empty '{{}}'" }
            val segments = splitOutsideQuotes(text, '|')
            val (root, steps) = parsePathSteps(segments.first().trim(), text)
            val filters = segments.drop(1).map { segment ->
                val pieces = splitOutsideQuotes(segment.trim(), ':')
                val name = pieces.first().trim()
                require(IDENTIFIER.matches(name)) { "invalid filter name '$name' in \"{{$text}}\"" }
                FilterCall(name, pieces.drop(1).map { parseArg(it.trim(), text) })
            }
            return Expression(root, steps, filters, text)
        }

        private fun parsePathSteps(path: String, text: String): Pair<String, List<PathStep>> {
            val root = ROOT.find(path)?.value
            require(root != null) { "invalid path '$path' in \"{{$text}}\"" }
            val steps = mutableListOf<PathStep>()
            var i = root.length
            while (i < path.length) {
                val match = STEP.matchAt(path, i)
                require(match != null) { "invalid path '$path' in \"{{$text}}\" at position $i" }
                steps += match.groupValues[1].takeIf { it.isNotEmpty() }?.let { PathStep.Key(it) }
                    ?: PathStep.Index(match.groupValues[2].toInt())
                i += match.value.length
            }
            return root to steps
        }

        private fun parseArg(arg: String, text: String): String = when {
            arg.length >= 2 && arg.startsWith("'") && arg.endsWith("'") -> arg.substring(1, arg.length - 1)
            NUMBER.matches(arg) || IDENTIFIER.matches(arg) -> arg
            else -> throw IllegalArgumentException(
                "filter argument $arg must be a number, a name or 'quoted' in \"{{$text}}\"",
            )
        }

        private fun splitOutsideQuotes(text: String, separator: Char): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            for (c in text) {
                when {
                    c == '\'' -> {
                        quoted = !quoted
                        current.append(c)
                    }
                    c == separator && !quoted -> {
                        result += current.toString()
                        current.clear()
                    }
                    else -> current.append(c)
                }
            }
            require(!quoted) { "unclosed quote in \"{{$text}}\"" }
            result += current.toString()
            return result
        }

        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val ROOT = Regex("""^(\${'$'}index|[A-Za-z_][A-Za-z0-9_-]*)""")
        private val STEP = Regex("""\.([A-Za-z0-9_${'$'}-]+)|\[(\d+)]""")
        private val NUMBER = Regex("""-?\d+(\.\d+)?""")
    }
}

/** Names usable as data-source, param, lookup and repeat names. */
internal val BINDING_NAME = Regex("[A-Za-z_][A-Za-z0-9_-]*")

/** Roots that are not data-source names. */
internal object Roots {
    const val PARAMS = "params"
    const val SECRET = "secret"
    const val INDEX = "\$index"
    val RESERVED = setOf(PARAMS, SECRET, INDEX)
}
