package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A screen as authored: by the Kotlin DSL (`sdk-server`), by JSON files, later by the admin panel
 * (ADR 0011). This is the product's source format, independent of any renderer (ADR 0015): the
 * server turns it into a Remote Compose document; it is never sent to apps as-is.
 *
 * JSON form ([ScreenJson]): polymorphic types use the `"type"` discriminator, e.g.
 * `{"id":"main","components":[{"type":"text","text":"Hi","style":"h1"}]}`.
 */
@Serializable
data class ScreenDefinition(
    val id: String,
    val title: String = id,
    /** Query parameters the screen reads as `{{params.x}}` (T-006). */
    val params: Map<String, ParamSpec> = emptyMap(),
    /** HTTP JSON sources, by name; bound as `{{name.path}}` (T-006, ADR 0008). */
    val data: Map<String, DataSource> = emptyMap(),
    /** Opt-in: apps keep the screen current over a WebSocket (T-047, ADR 0029); null = a normal screen. */
    val live: Live? = null,
    /** Value tables for the `lookup` filter; key `"*"` is the fallback. */
    val lookups: Map<String, Map<String, String>> = emptyMap(),
    /** Reactive on-device numeric values: written by controls (e.g. `slider`), read as `{{values.<name>}}`. */
    val values: Map<String, ScreenValue> = emptyMap(),
    val components: List<Component> = emptyList(),
    /** Shown instead of [components] when a non-optional data source fails. */
    val error: List<Component> = emptyList(),
) {
    /**
     * Whether the screen depends on the request: params, data sources or `{{ }}` bindings. Such a
     * screen is rendered per request; others are rendered once at load.
     */
    val isBound: Boolean
        get() = params.isNotEmpty() || data.isNotEmpty() || Template.hasBindings(title) ||
            values.values.any { Template.hasBindings(it.default) } ||
            (components + error).any { it.hasBindings() }

    /**
     * Every problem in this definition, each prefixed with a JSON-like path
     * (`components[2].action: …`); empty when valid. The only validation of screen content —
     * used by the DSL, the JSON loader and (later) the admin panel.
     */
    fun problems(): List<String> = contentProblems() + bindingProblems()

    /**
     * Structural problems only (no template checks): for screens whose strings are already bound
     * to data, where a `{{` is just text from an API.
     */
    fun contentProblems(): List<String> = screenContentProblems()

    /** Non-fatal accessibility/authoring warnings (surfaced in `/screens/{id}/debug`, T-016). */
    fun warnings(): List<String> = screenWarnings()

    /** Throws [IllegalArgumentException] listing [problems], if any. */
    fun validate() {
        val problems = problems()
        require(problems.isEmpty()) { "Screen '$id': ${problems.joinToString("; ")}" }
    }
}

/**
 * A live screen (T-047, ADR 0029): the server renders it again every [everyMs] ms (data sources
 * still respect their `cacheSeconds`) and pushes what changed to connected apps.
 */
@Serializable
data class Live(val everyMs: Int = DEFAULT_EVERY_MS) {
    companion object {
        const val DEFAULT_EVERY_MS = 1000
        const val MIN_EVERY_MS = 100
        const val MAX_EVERY_MS = 3_600_000
    }
}

/**
 * A reactive numeric value living on the device (no server round-trip): written by controls such as
 * [Component.Slider] or driven automatically by [animate], and read via `{{values.<name>}}` in
 * gauges, texts and KPIs. [default] is a number or a `{{...}}` server binding resolved at request time.
 */
@Serializable
data class ScreenValue(
    val default: String = "0",
    val min: Float = 0f,
    val max: Float = 100f,
    val animate: ValueAnimation? = null,
)

/** Automatic on-device animation of a [ScreenValue] from `min` to `max` using RC `animationTime()`. */
@Serializable
data class ValueAnimation(
    val durationMs: Int = DEFAULT_DURATION_MS,
    val mode: AnimationMode = AnimationMode.PingPong,
    val easing: AnimationEasing = AnimationEasing.EaseInOut,
) {
    companion object {
        const val DEFAULT_DURATION_MS = 2000
        const val MIN_DURATION_MS = 100
        const val MAX_DURATION_MS = 60_000
    }
}

@Serializable
enum class AnimationMode {
    @SerialName("once")
    Once,

    @SerialName("loop")
    Loop,

    @SerialName("pingPong")
    PingPong,
}

@Serializable
enum class AnimationEasing {
    @SerialName("linear")
    Linear,

    @SerialName("easeInOut")
    EaseInOut,
}

/** The JSON format of [ScreenDefinition]: strict (unknown keys are errors), defaults omitted. */
object ScreenJson {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(screen: ScreenDefinition): String = json.encodeToString(ScreenDefinition.serializer(), screen)

    /**
     * Decodes and validates one screen. Any problem throws [IllegalArgumentException] whose message
     * starts with [source] (e.g. the file name) and contains the JSON path.
     */
    fun decode(text: String, source: String): ScreenDefinition {
        val screen = try {
            json.decodeFromString(ScreenDefinition.serializer(), text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("$source: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$source: ${e.message}", e)
        }
        val problems = screen.problems()
        require(problems.isEmpty()) { "$source: ${problems.joinToString("; ")}" }
        return screen
    }

    /** The result of [check]: [screen] when [problems] is empty. */
    data class Checked(val screen: ScreenDefinition?, val problems: List<String>)

    /**
     * Checks [text] like [decode] without throwing (the admin editor, T-007): a JSON syntax or
     * schema error is one problem (kotlinx's message, with the JSON path); otherwise every
     * [ScreenDefinition.problems] entry.
     */
    fun check(text: String): Checked {
        val screen = try {
            json.decodeFromString(ScreenDefinition.serializer(), text)
        } catch (e: SerializationException) {
            return Checked(null, listOf(e.message ?: "invalid JSON"))
        } catch (e: IllegalArgumentException) {
            return Checked(null, listOf(e.message ?: "invalid value"))
        }
        val problems = screen.problems()
        return Checked(screen.takeIf { problems.isEmpty() }, problems)
    }
}
