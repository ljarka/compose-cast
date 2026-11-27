package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A building block of a screen. New components: docs/playbooks/add-component.md. */
@Serializable
public sealed interface Component {
    /** Layout and decoration (T-012); every component except [Repeat] has one. */
    public val modifier: Modifier get() = Modifier.NONE

    @Serializable
    @SerialName("text")
    public data class Text(
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
    public data class Image(
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
    public data class Icon(
        val url: String,
        val alt: String = "",
        val size: Int = 24,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Horizontal rule of [thickness] dp (default 1). */
    @Serializable
    @SerialName("divider")
    public data class Divider(
        val thickness: Int = 1,
        val color: String? = null,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Vertical container with card defaults (16 dp padding, 12 dp corner radius, surface background). */
    @Serializable
    @SerialName("card")
    public data class Card(
        val children: List<Component>,
        val spacing: Int = 0,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    @Serializable
    @SerialName("button")
    public data class Button(
        val label: String,
        val action: Action,
        val variant: ButtonVariant = ButtonVariant.Filled,
        val icon: String? = null,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Empty space of [height] dp along the parent's direction (a width inside a row). */
    @Serializable
    @SerialName("spacer")
    public data class Spacer(val height: Int = 8, override val modifier: Modifier = Modifier.NONE) : Component

    /** Children stacked vertically, [spacing] dp apart (T-012). */
    @Serializable
    @SerialName("column")
    public data class Column(
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
    public data class Row(
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
    public data class Box(
        val children: List<Component>,
        val horizontalAlignment: Alignment = Alignment.Start,
        val verticalAlignment: Alignment = Alignment.Start,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Shows the first child that fits within the available bounds (T-015). */
    @Serializable
    @SerialName("fitBox")
    public data class FitBox(
        val children: List<Component>,
        val horizontalAlignment: Alignment = Alignment.Start,
        val verticalAlignment: Alignment = Alignment.Start,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** Children laid out horizontally and wrapped onto new lines (T-014). */
    @Serializable
    @SerialName("flow")
    public data class Flow(
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
    public data class Grid(
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
    public data class Repeat(
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
    public data class Expandable(
        val title: String,
        val content: List<Component>,
        val expanded: Boolean = false,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /**
     * A ring filled to [value] between [min] and [max], drawn on an RC canvas, with [label] in the
     * middle (T-047). [value] is a number or a binding (`"{{metrics.cpu}}"`); the ring animates to
     * a new value on live screens (ADR 0029).
     */
    @Serializable
    @SerialName("gauge")
    public data class Gauge(
        val value: String,
        val min: Float = 0f,
        val max: Float = 100f,
        val label: String = "",
        val color: String? = null,
        val trackColor: String? = null,
        val size: Int = 120,
        val thickness: Int = 12,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /**
     * A line through [values], full width and [height] dp high (T-047). [values] holds the numbers
     * (`"3, 5, 2, 8"`) or binds a JSON array (`"{{metrics.history}}"`); [NumberList] reads them.
     * The vertical range is [min]..[max], or the values' own range when not set.
     */
    @Serializable
    @SerialName("sparkline")
    public data class Sparkline(
        val values: String,
        val min: Float? = null,
        val max: Float? = null,
        val color: String? = null,
        val height: Int = 64,
        val thickness: Int = 2,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** A key figure: small [label], big [value], optional [caption] below (T-047). All bindable. */
    @Serializable
    @SerialName("kpi")
    public data class Kpi(
        val value: String,
        val label: String = "",
        val caption: String = "",
        val color: String? = null,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /**
     * Horizontal slider drawn on an RC canvas that updates screen value [controls] locally on the
     * device via `TouchExpression` (no server round-trip). Optional [step] snaps to evenly spaced
     * notches across the value's `min..max` range with haptic ticks.
     */
    @Serializable
    @SerialName("slider")
    public data class Slider(
        val controls: String,
        val step: Float? = null,
        val color: String? = null,
        val trackColor: String? = null,
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /**
     * A [ComponentDefinition] (raw RC JSON body, ADR 0033) used by id; [args] (bindable strings)
     * fill its params. [modifier] applies to a box around the body.
     */
    @Serializable
    @SerialName("custom")
    public data class Custom(
        val component: String,
        val args: Map<String, String> = emptyMap(),
        override val modifier: Modifier = Modifier.NONE,
    ) : Component

    /** The bindable string fields of this component (not of nested ones), by field path. */
    public fun strings(): List<Pair<String, String>> = when (this) {
        is Text -> listOf("text" to text)
        is Image -> listOf("url" to url, "alt" to alt)
        is Icon -> listOf("url" to url, "alt" to alt)
        is Button -> listOf("label" to label) +
            listOfNotNull(icon?.let { "icon" to it }) +
            action.strings().map { (field, value) -> "action.$field" to value }
        is Expandable -> listOf("title" to title)
        is Gauge -> listOf("value" to value, "label" to label)
        is Sparkline -> listOf("values" to values)
        is Kpi -> listOf("value" to value, "label" to label, "caption" to caption)
        is Custom -> args.map { (k, v) -> "args.$k" to v }
        is Divider, is Card, is Spacer, is Repeat, is Column, is Row, is Box, is FitBox, is Flow, is Grid,
        is Slider,
        -> emptyList()
    } + modifier.action?.strings().orEmpty().map { (field, value) -> "modifier.action.$field" to value } +
        listOfNotNull(
            modifier.semantics?.description?.let { "modifier.semantics.description" to it },
            modifier.semantics?.stateDescription?.let { "modifier.semantics.stateDescription" to it },
            modifier.visibility?.id?.let { "modifier.visibility.id" to it },
        )

    /** Nested component lists by field name (`children`, `content`, `template`, `empty`). */
    public fun childLists(): List<Pair<String, List<Component>>> = when (this) {
        is Column -> listOf("children" to children)
        is Row -> listOf("children" to children)
        is Box -> listOf("children" to children)
        is FitBox -> listOf("children" to children)
        is Card -> listOf("children" to children)
        is Flow -> listOf("children" to children)
        is Grid -> listOf("children" to children)
        is Expandable -> listOf("content" to content)
        is Repeat -> listOf("template" to template, "empty" to empty)
        is Text, is Image, is Icon, is Divider, is Button, is Spacer, is Gauge, is Sparkline, is Kpi,
        is Slider, is Custom,
        -> emptyList()
    }

    /** Whether this component or a nested one contains `{{`, or it is a [Repeat]. */
    public fun hasBindings(): Boolean = this is Repeat || strings().any { Template.hasBindings(it.second) } ||
        childLists().any { (_, list) -> list.any { it.hasBindings() } }
}

/**
 * Numbers in a bound string, in order: `"3, 5.5, -2"`, a JSON array `"[3,5.5,-2]"` (what a bound
 * array renders to) or one number. Used by validation and the renderer (gauge, sparkline).
 */
public object NumberList {
    private val NUMBER = Regex("""-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?""")

    public fun parse(text: String): List<Float> = NUMBER.findAll(text).mapNotNull { it.value.toFloatOrNull() }.toList()

    /** The single number in [text], or null when it is not exactly one number. */
    public fun single(text: String): Float? = text.trim().toFloatOrNull()?.takeIf { it.isFinite() }
}

/** The bindable string fields of an action, by field path. */
public fun Action.strings(): List<Pair<String, String>> = when (this) {
    is Action.Navigate -> listOf("screen" to screen) + params.map { (k, v) -> "params.$k" to v }
    is Action.OpenUrl -> listOf("url" to url)
    is Action.Reload -> emptyList()
    is Action.Track -> listOf("event" to event) + params.map { (k, v) -> "params.$k" to v }
    is Action.Custom -> listOf("payload" to payload)
    is Action.Toggle -> listOf("target" to target)
    is Action.Show -> listOf("target" to target)
    is Action.Hide -> listOf("target" to target)
}
