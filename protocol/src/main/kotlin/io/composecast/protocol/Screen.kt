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
    /** Value tables for the `lookup` filter; key `"*"` is the fallback. */
    val lookups: Map<String, Map<String, String>> = emptyMap(),
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
    fun contentProblems(): List<String> = buildList {
        if (id.isBlank()) add("id: must not be blank")
        addAll(componentProblems(components, "components"))
        addAll(componentProblems(error, "error"))
    }

    /** Throws [IllegalArgumentException] listing [problems], if any. */
    fun validate() {
        val problems = problems()
        require(problems.isEmpty()) { "Screen '$id': ${problems.joinToString("; ")}" }
    }

    private fun componentProblems(list: List<Component>, path: String): List<String> = buildList {
        list.forEachIndexed { i, component ->
            val at = "$path[$i]"
            when (component) {
                is Component.Text -> Unit
                is Component.Image -> if (component.url.isBlank()) add("$at.url: must not be blank")
                is Component.Button -> actionProblem(component.action)?.let { add("$at.action: $it") }
                is Component.Spacer ->
                    if (component.height < 0) add("$at.height: must be >= 0, was ${component.height}")
                is Component.Repeat -> {
                    if ((component.limit ?: 0) < 0) add("$at.limit: must be >= 0, was ${component.limit}")
                    if (component.items is RepeatItems.Zip && component.items.arrays.isEmpty()) {
                        add("$at.items: must name at least one array")
                    }
                    addAll(componentProblems(component.template, "$at.template"))
                    addAll(componentProblems(component.empty, "$at.empty"))
                }
                is Component.Expandable -> {
                    if (component.title.isBlank()) add("$at.title: must not be blank")
                    if (component.content.isEmpty()) add("$at.content: must not be empty")
                    addAll(componentProblems(component.content, "$at.content"))
                }
            }
        }
    }

    private fun actionProblem(action: Action): String? {
        val encoded = ActionCodec.encode(action)
        return when {
            encoded.length > ActionCodec.MAX_ENCODED_LENGTH ->
                "encoded action is ${encoded.length} characters, Remote Compose allows ${ActionCodec.MAX_ENCODED_LENGTH}"
            // Catches empty screen/event names, empty param keys, empty URLs: anything the app
            // could not decode back to the same action.
            ActionCodec.decode(encoded) != action -> "$action cannot be carried to the app (empty name, key or URL?)"
            else -> null
        }
    }

    /** Template syntax, names, scopes, filters, lookups, secrets and source URLs (T-006). */
    private fun bindingProblems(): List<String> = buildList {
        fun name(kind: String, at: String, value: String) {
            if (!BINDING_NAME.matches(value)) add("$at: invalid $kind name '$value' (letters, digits, _ and -)")
            if (value in Roots.RESERVED) add("$at: '$value' is reserved")
        }
        params.keys.forEach { name("param", "params.$it", it) }
        lookups.keys.forEach { name("lookup", "lookups.$it", it) }
        data.forEach { (source, spec) ->
            val at = "data.$source"
            name("data source", at, source)
            if (spec.timeoutMs !in 1..60_000) add("$at.timeoutMs: must be 1..60000, was ${spec.timeoutMs}")
            if (spec.cacheSeconds < 0) add("$at.cacheSeconds: must be >= 0, was ${spec.cacheSeconds}")
            urlProblem(spec.url)?.let { add("$at.url: $it") }
            val sourceRoots = setOf(Roots.PARAMS, Roots.SECRET)
            addAll(templateProblems(spec.url, "$at.url", sourceRoots))
            spec.headers.forEach { (header, value) ->
                addAll(templateProblems(value, "$at.headers.$header", sourceRoots))
            }
        }
        val roots = setOf(Roots.PARAMS) + data.keys
        addAll(templateProblems(title, "title", roots))
        addAll(componentBindingProblems(components, "components", roots))
        addAll(componentBindingProblems(error, "error", setOf(Roots.PARAMS)))
    }

    private fun componentBindingProblems(list: List<Component>, path: String, roots: Set<String>): List<String> =
        buildList {
            list.forEachIndexed { i, component ->
                val at = "$path[$i]"
                component.strings().forEach { (field, value) -> addAll(templateProblems(value, "$at.$field", roots)) }
                if (component is Component.Repeat) {
                    component.items.paths().forEach { (suffix, itemPath) ->
                        try {
                            val expression = Template.parsePath(itemPath)
                            if (expression.root !in roots) {
                                add("$at.items$suffix: ${unknownRoot(expression.root, roots)}")
                            }
                        } catch (e: IllegalArgumentException) {
                            add("$at.items$suffix: ${e.message}")
                        }
                    }
                    val alias = component.alias
                    if (!BINDING_NAME.matches(alias) || alias in Roots.RESERVED) {
                        add("$at.as: invalid name '$alias'")
                    } else if (alias in roots) {
                        add("$at.as: '$alias' hides a data source or an outer repeat")
                    }
                    addAll(componentBindingProblems(component.template, "$at.template", roots + alias + Roots.INDEX))
                    addAll(componentBindingProblems(component.empty, "$at.empty", roots))
                }
                if (component is Component.Expandable) {
                    addAll(componentBindingProblems(component.content, "$at.content", roots))
                }
            }
        }

    private fun templateProblems(text: String, at: String, roots: Set<String>): List<String> = buildList {
        if (!Template.hasBindings(text)) return@buildList
        val template = try {
            Template.parse(text)
        } catch (e: IllegalArgumentException) {
            add("$at: ${e.message}")
            return@buildList
        }
        template.expressions.forEach { e ->
            val where = "$at: {{${e.text}}}"
            when {
                e.root == Roots.SECRET && Roots.SECRET !in roots ->
                    add("$where: secrets are only allowed in data source urls and headers")
                e.root !in roots -> add("$where: ${unknownRoot(e.root, roots)}")
                e.root == Roots.PARAMS -> {
                    val param = (e.path.singleOrNull() as? PathStep.Key)?.name
                    if (param == null) {
                        add("$where: use {{params.<name>}}")
                    } else if (param !in params) {
                        add("$where: unknown param '$param' (declare it in \"params\")")
                    }
                }
                e.root == Roots.SECRET && (e.path.singleOrNull() !is PathStep.Key) ->
                    add("$where: use {{secret.<NAME>}}")
            }
            e.filters.forEach { f ->
                val arity = Filters.ARITY[f.name]
                when {
                    arity == null ->
                        add("$where: unknown filter '${f.name}' (known: ${Filters.ARITY.keys.joinToString()})")
                    f.args.size !in arity ->
                        add("$where: filter '${f.name}' takes ${arity.first}..${arity.last} arguments")
                    f.name == "lookup" && f.args.single() !in lookups ->
                        add("$where: unknown lookup '${f.args.single()}' (declare it in \"lookups\")")
                    f.name == "round" && f.args.isNotEmpty() && f.args.single().toIntOrNull() !in 0..10 ->
                        add("$where: round digits must be 0..10")
                }
            }
        }
    }

    private fun unknownRoot(root: String, roots: Set<String>) =
        "unknown '$root' (available here: ${roots.sorted().joinToString()})"

    /** Scheme and host must be literal: request values must not choose the server (SSRF). */
    private fun urlProblem(url: String): String? {
        val scheme = listOf("https://", "http://").firstOrNull { url.startsWith(it) }
            ?: return "must start with http:// or https://"
        val hostEnd = url.indexOfAny(charArrayOf('/', '?', '#'), scheme.length).let { if (it < 0) url.length else it }
        val host = url.substring(scheme.length, hostEnd)
        val literal = host.isNotEmpty() && "{" !in host && "}" !in host
        return if (literal) null else "host must be literal (no {{ }} before the path)"
    }
}

/** A building block of a screen. New components: docs/playbooks/add-component.md. */
@Serializable
sealed interface Component {
    @Serializable
    @SerialName("text")
    data class Text(val text: String, val style: TextStyle = TextStyle.Body) : Component

    /** Fetched by the server and embedded in the document (ADR 0003). */
    @Serializable
    @SerialName("image")
    data class Image(val url: String, val alt: String = "") : Component

    @Serializable
    @SerialName("button")
    data class Button(val label: String, val action: Action) : Component

    /** Vertical space in dp. */
    @Serializable
    @SerialName("spacer")
    data class Spacer(val height: Int = 8) : Component

    /**
     * [template] once per element of [items] (at most [limit]), each bound with the element as
     * `{{<alias>}}` and its position as `{{$index}}`; [empty] when there are none. Expanded on the
     * server before rendering (T-006).
     */
    @Serializable
    @SerialName("repeat")
    data class Repeat(
        val items: RepeatItems,
        @SerialName("as") val alias: String = "item",
        val limit: Int? = null,
        val template: List<Component>,
        val empty: List<Component> = emptyList(),
    ) : Component

    /**
     * A section whose header toggles [content] locally on the device (`StateLayout`, no server
     * round-trip — T-008). Starts open when [expanded] is true.
     */
    @Serializable
    @SerialName("expandable")
    data class Expandable(val title: String, val content: List<Component>, val expanded: Boolean = false) : Component

    /** The bindable string fields of this component (not of nested ones), by field path. */
    fun strings(): List<Pair<String, String>> = when (this) {
        is Text -> listOf("text" to text)
        is Image -> listOf("url" to url, "alt" to alt)
        is Button -> listOf("label" to label) + action.strings().map { (field, value) -> "action.$field" to value }
        is Expandable -> listOf("title" to title)
        is Spacer, is Repeat -> emptyList()
    }

    /** Whether this component or a nested one contains `{{`, or it is a [Repeat]. */
    fun hasBindings(): Boolean = this is Repeat || strings().any { Template.hasBindings(it.second) } ||
        (this is Expandable && content.any { it.hasBindings() })
}

/** The bindable string fields of an action, by field path. */
fun Action.strings(): List<Pair<String, String>> = when (this) {
    is Action.Navigate -> listOf("screen" to screen) + params.map { (k, v) -> "params.$k" to v }
    is Action.OpenUrl -> listOf("url" to url)
    is Action.Reload -> emptyList()
    is Action.Track -> listOf("event" to event) + params.map { (k, v) -> "params.$k" to v }
    is Action.Custom -> listOf("payload" to payload)
}

/** Text styles of [Component.Text]; [key] is the JSON / DSL name. */
@Serializable
enum class TextStyle(val key: String) {
    @SerialName("h1")
    H1("h1"),

    @SerialName("h2")
    H2("h2"),

    @SerialName("h3")
    H3("h3"),

    @SerialName("body")
    Body("body"),

    @SerialName("caption")
    Caption("caption"),
    ;

    companion object {
        fun fromKey(key: String): TextStyle {
            val allowed = entries.joinToString { it.key }
            return entries.firstOrNull { it.key == key }
                ?: throw IllegalArgumentException("Unknown text style '$key'. Allowed: $allowed")
        }
    }
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
}
