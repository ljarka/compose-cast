package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    } + modifier.action?.strings().orEmpty().map { (field, value) -> "modifier.action.$field" to value } +
        listOfNotNull(
            modifier.semantics?.description?.let { "modifier.semantics.description" to it },
            modifier.semantics?.stateDescription?.let { "modifier.semantics.stateDescription" to it },
        )

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
