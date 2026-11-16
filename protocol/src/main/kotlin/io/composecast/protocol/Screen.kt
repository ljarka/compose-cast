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

    /** Where a component list sits: decides whether `weight` is allowed. */
    private enum class Parent { Screen, Column, Row, Other }

    private fun componentProblems(
        list: List<Component>,
        path: String,
        parent: Parent = Parent.Screen,
        depth: Int = 1,
    ): List<String> = buildList {
        if (depth > MAX_DEPTH) {
            add("$path: nested deeper than $MAX_DEPTH levels")
            return@buildList
        }
        list.forEachIndexed { i, component ->
            val at = "$path[$i]"
            if (component !is Component.Repeat) addAll(modifierProblems(component.modifier, "$at.modifier", parent))
            when (component) {
                is Component.Text -> {
                    component.color?.let { colorProblem(it)?.let { problem -> add("$at.color: $problem") } }
                    component.fontSize?.let { if (it <= 0) add("$at.fontSize: must be > 0, was $it") }
                    component.maxLines?.let { if (it <= 0) add("$at.maxLines: must be > 0, was $it") }
                }
                is Component.Image -> {
                    if (component.url.isBlank()) add("$at.url: must not be blank")
                    component.alpha?.let { if (it.isNaN() || it !in 0f..1f) add("$at.alpha: must be 0..1, was $it") }
                    component.aspectRatio?.let {
                        if (!(it > 0f) || it.isInfinite()) add("$at.aspectRatio: must be > 0, was $it")
                    }
                }
                is Component.Icon -> {
                    if (component.url.isBlank()) add("$at.url: must not be blank")
                    if (component.size <= 0) add("$at.size: must be > 0, was ${component.size}")
                }
                is Component.Divider -> {
                    if (component.thickness <= 0) add("$at.thickness: must be > 0, was ${component.thickness}")
                    component.color?.let { colorProblem(it)?.let { problem -> add("$at.color: $problem") } }
                }
                is Component.Button -> {
                    actionProblem(component.action)?.let { add("$at.action: $it") }
                    if (component.icon != null && component.icon.isBlank()) add("$at.icon: must not be blank")
                }
                is Component.Card -> {
                    if (component.spacing < 0) add("$at.spacing: must be >= 0, was ${component.spacing}")
                    addAll(componentProblems(component.children, "$at.children", Parent.Column, depth + 1))
                }
                is Component.Spacer ->
                    if (component.height < 0) add("$at.height: must be >= 0, was ${component.height}")
                is Component.Repeat -> {
                    if ((component.limit ?: 0) < 0) add("$at.limit: must be >= 0, was ${component.limit}")
                    if (component.items is RepeatItems.Zip && component.items.arrays.isEmpty()) {
                        add("$at.items: must name at least one array")
                    }
                    // Expanded in place: its components are children of this repeat's parent.
                    addAll(componentProblems(component.template, "$at.template", parent, depth))
                    addAll(componentProblems(component.empty, "$at.empty", parent, depth))
                }
                is Component.Expandable -> {
                    if (component.title.isBlank()) add("$at.title: must not be blank")
                    if (component.content.isEmpty()) add("$at.content: must not be empty")
                    addAll(componentProblems(component.content, "$at.content", Parent.Column, depth + 1))
                }
                is Component.Column -> {
                    if (component.spacing < 0) add("$at.spacing: must be >= 0, was ${component.spacing}")
                    addAll(componentProblems(component.children, "$at.children", Parent.Column, depth + 1))
                }
                is Component.Row -> {
                    if (component.spacing < 0) add("$at.spacing: must be >= 0, was ${component.spacing}")
                    addAll(componentProblems(component.children, "$at.children", Parent.Row, depth + 1))
                }
                is Component.Box -> addAll(
                    componentProblems(component.children, "$at.children", Parent.Other, depth + 1),
                )
                is Component.FitBox -> addAll(
                    componentProblems(component.children, "$at.children", Parent.Other, depth + 1),
                )
                is Component.Flow -> {
                    component.maxItemsInEachRow?.let {
                        if (it < 1) add("$at.maxItemsInEachRow: must be >= 1, was $it")
                    }
                    component.maxLines?.let { if (it < 1) add("$at.maxLines: must be >= 1, was $it") }
                    addAll(componentProblems(component.children, "$at.children", Parent.Row, depth + 1))
                }
                is Component.Grid -> {
                    if (component.columns !in 1..12) {
                        add("$at.columns: must be 1..12, was ${component.columns}")
                    }
                    if (component.spacing < 0) add("$at.spacing: must be >= 0, was ${component.spacing}")
                    addAll(componentProblems(component.children, "$at.children", Parent.Other, depth + 1))
                }
            }
        }
    }

    private fun modifierProblems(m: Modifier, at: String, parent: Parent): List<String> = buildList {
        m.padding?.let { p ->
            if (listOf(p.start, p.top, p.end, p.bottom).any { it < 0 }) add("$at.padding: must be >= 0, was $p")
        }
        listOf("width" to m.width, "height" to m.height).forEach { (field, dimension) ->
            if (dimension is Dimension.Dp &&
                dimension.value < 0
            ) {
                add("$at.$field: must be >= 0, was ${dimension.value}")
            }
        }
        m.weight?.let { weight ->
            if (!(weight > 0f) || weight.isInfinite()) add("$at.weight: must be > 0, was $weight")
            if (parent != Parent.Column && parent != Parent.Row) {
                add("$at.weight: only allowed for direct children of a row or column")
            }
        }
        m.background?.let { colorProblem(it)?.let { problem -> add("$at.background: $problem") } }
        m.cornerRadius?.let { if (it < 0) add("$at.cornerRadius: must be >= 0, was $it") }
        if (m.cornerRadius != null && m.shape != null) add("$at: set either cornerRadius or shape, not both")
        m.border?.let { border ->
            if (border.width <= 0) add("$at.border.width: must be > 0, was ${border.width}")
            colorProblem(border.color)?.let { add("$at.border.color: $it") }
        }
        m.action?.let { action -> actionProblem(action)?.let { add("$at.action: $it") } }
        m.minWidth?.let { if (it < 0) add("$at.minWidth: must be >= 0, was $it") }
        m.maxWidth?.let { if (it < 0) add("$at.maxWidth: must be >= 0, was $it") }
        if (m.minWidth != null && m.maxWidth != null && m.minWidth > m.maxWidth) {
            add("$at.minWidth: must be <= maxWidth (${m.maxWidth}), was ${m.minWidth}")
        }
        m.minHeight?.let { if (it < 0) add("$at.minHeight: must be >= 0, was $it") }
        m.maxHeight?.let { if (it < 0) add("$at.maxHeight: must be >= 0, was $it") }
        if (m.minHeight != null && m.maxHeight != null && m.minHeight > m.maxHeight) {
            add("$at.minHeight: must be <= maxHeight (${m.maxHeight}), was ${m.minHeight}")
        }
        m.fillFraction?.let {
            if (it.isNaN() || it <= 0f || it > 1f) add("$at.fillFraction: must be in (0, 1], was $it")
        }
        m.collapsePriority?.let {
            if (it.isNaN() || it.isInfinite()) add("$at.collapsePriority: must be finite, was $it")
        }
    }

    private fun colorProblem(color: String): String? =
        if (COLOR.matches(color)) null else "expected #RRGGBB or #AARRGGBB, was '$color'"

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
                if (component !is Component.Repeat) {
                    component.childLists().forEach { (field, children) ->
                        addAll(componentBindingProblems(children, "$at.$field", roots))
                    }
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
    /** Layout and decoration (T-012); every component except [Repeat] has one. */
    val modifier: Modifier get() = Modifier.NONE

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        val style: TextStyle = TextStyle.Body,
        val color: String? = null,
        val fontWeight: FontWeight? = null,
        val fontSize: Int? = null,
        val textAlign: TextAlign? = null,
        val maxLines: Int? = null,
        val overflow: TextOverflow? = null,
        val marquee: Boolean = false,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /**
     * Fetched by the server and embedded in the document (ADR 0003). Without a `modifier.height`
     * its height follows [aspectRatio] or the image's natural aspect ratio at the screen's full
     * width — inside rows give it a height or [aspectRatio] with an explicit width.
     */
    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        val alt: String = "",
        val contentScale: ContentScale? = null,
        val alpha: Float? = null,
        val aspectRatio: Float? = null,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Small image rendered with `Fit` scaling at [size] dp (default 24). */
    @Serializable
    @SerialName("icon")
    data class Icon(
        val url: String,
        val alt: String = "",
        val size: Int = 24,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Horizontal rule of [thickness] dp (default 1). */
    @Serializable
    @SerialName("divider")
    data class Divider(
        val thickness: Int = 1,
        val color: String? = null,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Vertical container with card defaults (16 dp padding, 12 dp corner radius, surface background). */
    @Serializable
    @SerialName("card")
    data class Card(
        val children: List<Component>,
        val spacing: Int = 0,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    @Serializable
    @SerialName("button")
    data class Button(
        val label: String,
        val action: Action,
        val variant: ButtonVariant = ButtonVariant.Filled,
        val icon: String? = null,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Empty space of [height] dp along the parent's direction (a width inside a row). */
    @Serializable
    @SerialName("spacer")
    data class Spacer(val height: Int = 8, override val modifier: Modifier = Modifier.NONE) : Component

    /** Children stacked vertically, [spacing] dp apart (T-012). */
    @Serializable
    @SerialName("column")
    data class Column(
        val children: List<Component>,
        val horizontalAlignment: Alignment = Alignment.Start,
        val verticalArrangement: Arrangement = Arrangement.Start,
        val spacing: Int = 0,
        val collapsible: Boolean = false,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Children side by side, [spacing] dp apart (T-012). */
    @Serializable
    @SerialName("row")
    data class Row(
        val children: List<Component>,
        val horizontalArrangement: Arrangement = Arrangement.Start,
        val verticalAlignment: Alignment = Alignment.Start,
        val spacing: Int = 0,
        val collapsible: Boolean = false,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Children stacked on top of each other (later ones above), aligned in the box (T-012). */
    @Serializable
    @SerialName("box")
    data class Box(
        val children: List<Component>,
        val horizontalAlignment: Alignment = Alignment.Start,
        val verticalAlignment: Alignment = Alignment.Start,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Shows the first child that fits within the available bounds (T-015). */
    @Serializable
    @SerialName("fitBox")
    data class FitBox(
        val children: List<Component>,
        val horizontalAlignment: Alignment = Alignment.Start,
        val verticalAlignment: Alignment = Alignment.Start,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Children laid out horizontally and wrapped onto new lines (T-014). */
    @Serializable
    @SerialName("flow")
    data class Flow(
        val children: List<Component>,
        val horizontalAlignment: Alignment = Alignment.Start,
        val verticalAlignment: Alignment = Alignment.Start,
        val maxItemsInEachRow: Int? = null,
        val maxLines: Int? = null,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Equal-width grid of [columns] columns, [spacing] dp apart (T-014). */
    @Serializable
    @SerialName("grid")
    data class Grid(
        val columns: Int,
        val children: List<Component>,
        val spacing: Int = 0,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

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
    data class Expandable(
        val title: String,
        val content: List<Component>,
        val expanded: Boolean = false,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** The bindable string fields of this component (not of nested ones), by field path. */
    fun strings(): List<Pair<String, String>> = when (this) {
        is Text -> listOf("text" to text)
        is Image -> listOf("url" to url, "alt" to alt)
        is Icon -> listOf("url" to url, "alt" to alt)
        is Button -> listOf("label" to label) +
            listOfNotNull(icon?.let { "icon" to it }) +
            action.strings().map { (field, value) -> "action.$field" to value }
        is Expandable -> listOf("title" to title)
        is Divider, is Card, is Spacer, is Repeat, is Column, is Row, is Box, is FitBox, is Flow, is Grid -> emptyList()
    } + modifier.action?.strings().orEmpty().map { (field, value) -> "modifier.action.$field" to value }

    /** Nested component lists by field name (`children`, `content`, `template`, `empty`). */
    fun childLists(): List<Pair<String, List<Component>>> = when (this) {
        is Column -> listOf("children" to children)
        is Row -> listOf("children" to children)
        is Box -> listOf("children" to children)
        is FitBox -> listOf("children" to children)
        is Card -> listOf("children" to children)
        is Flow -> listOf("children" to children)
        is Grid -> listOf("children" to children)
        is Expandable -> listOf("content" to content)
        is Repeat -> listOf("template" to template, "empty" to empty)
        is Text, is Image, is Icon, is Divider, is Button, is Spacer -> emptyList()
    }

    /** Whether this component or a nested one contains `{{`, or it is a [Repeat]. */
    fun hasBindings(): Boolean = this is Repeat || strings().any { Template.hasBindings(it.second) } ||
        childLists().any { (_, list) -> list.any { it.hasBindings() } }
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

@Serializable
enum class FontWeight {
    @SerialName("thin")
    Thin,

    @SerialName("extraLight")
    ExtraLight,

    @SerialName("light")
    Light,

    @SerialName("normal")
    Normal,

    @SerialName("medium")
    Medium,

    @SerialName("semiBold")
    SemiBold,

    @SerialName("bold")
    Bold,

    @SerialName("extraBold")
    ExtraBold,

    @SerialName("black")
    Black,
}

@Serializable
enum class TextAlign {
    @SerialName("start")
    Start,

    @SerialName("center")
    Center,

    @SerialName("end")
    End,

    @SerialName("left")
    Left,

    @SerialName("right")
    Right,

    @SerialName("justify")
    Justify,
}

@Serializable
enum class TextOverflow {
    @SerialName("clip")
    Clip,

    @SerialName("visible")
    Visible,

    @SerialName("ellipsis")
    Ellipsis,

    @SerialName("startEllipsis")
    StartEllipsis,

    @SerialName("middleEllipsis")
    MiddleEllipsis,
}

@Serializable
enum class ContentScale {
    @SerialName("fit")
    Fit,

    @SerialName("crop")
    Crop,

    @SerialName("fillBounds")
    FillBounds,

    @SerialName("fillWidth")
    FillWidth,

    @SerialName("fillHeight")
    FillHeight,

    @SerialName("inside")
    Inside,

    @SerialName("none")
    None,
}

@Serializable
enum class ButtonVariant {
    @SerialName("filled")
    Filled,

    @SerialName("outlined")
    Outlined,

    @SerialName("text")
    Text,
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

/** Deepest allowed nesting of component lists (containers, expandables) — keeps documents sane. */
private const val MAX_DEPTH = 32

private val COLOR = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")
