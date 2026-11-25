package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.Alignment
import io.composecast.protocol.Arrangement
import io.composecast.protocol.ButtonVariant
import io.composecast.protocol.Component
import io.composecast.protocol.ContentScale
import io.composecast.protocol.DataSource
import io.composecast.protocol.FontWeight
import io.composecast.protocol.Live
import io.composecast.protocol.Modifier
import io.composecast.protocol.ParamSpec
import io.composecast.protocol.RepeatItems
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenValue
import io.composecast.protocol.ScrollDirection
import io.composecast.protocol.TextAlign
import io.composecast.protocol.TextOverflow
import io.composecast.protocol.TextStyle

@DslMarker
annotation class ScreenDslMarker

/**
 * Components of a screen, a `repeat` template or an `error` state. Strings may contain
 * `{{ … }}` bindings (T-006); they are validated when the screen is added to the registry.
 */
@ScreenDslMarker
open class ComponentsBuilder internal constructor(private val context: String) {
    internal val components = mutableListOf<Component>()

    /** [style]: `h1`, `h2`, `h3`, `body`, `caption` ([TextStyle]). */
    fun text(
        text: String,
        style: String = "body",
        color: String? = null,
        fontWeight: FontWeight? = null,
        fontSize: Int? = null,
        textAlign: TextAlign? = null,
        maxLines: Int? = null,
        overflow: TextOverflow? = null,
        marquee: Boolean = false,
        modifier: Modifier = Modifier.NONE,
    ) {
        components += Component.Text(
            text = text,
            style = TextStyle.fromKey(style),
            color = color,
            fontWeight = fontWeight,
            fontSize = fontSize,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            marquee = marquee,
            modifier = modifier,
        )
    }

    fun image(
        url: String,
        alt: String = "",
        contentScale: ContentScale? = null,
        alpha: Float? = null,
        aspectRatio: Float? = null,
        modifier: Modifier = Modifier.NONE,
    ) {
        components += Component.Image(url, alt, contentScale, alpha, aspectRatio, modifier)
    }

    /** Small image rendered with `Fit` scaling at [size] dp (default 24). */
    fun icon(url: String, alt: String = "", size: Int = 24, modifier: Modifier = Modifier.NONE) {
        components += Component.Icon(url, alt, size, modifier)
    }

    /** Horizontal rule of [thickness] dp (default 1). */
    fun divider(thickness: Int = 1, color: String? = null, modifier: Modifier = Modifier.NONE) {
        components += Component.Divider(thickness, color, modifier)
    }

    /** Ring filled to [value] (a number or a `{{binding}}`) between [min] and [max] (T-047). */
    fun gauge(
        value: String,
        min: Float = 0f,
        max: Float = 100f,
        label: String = "",
        color: String? = null,
        trackColor: String? = null,
        size: Int = 120,
        thickness: Int = 12,
        modifier: Modifier = Modifier.NONE,
    ) {
        components += Component.Gauge(value, min, max, label, color, trackColor, size, thickness, modifier)
    }

    /** Line through [values] (`"3, 5, 2"` or a `{{binding}}` to a JSON array of numbers) (T-047). */
    fun sparkline(
        values: String,
        min: Float? = null,
        max: Float? = null,
        color: String? = null,
        height: Int = 64,
        thickness: Int = 2,
        modifier: Modifier = Modifier.NONE,
    ) {
        components += Component.Sparkline(values, min, max, color, height, thickness, modifier)
    }

    /** Key figure: small [label], big [value], optional [caption] (T-047). */
    fun kpi(
        value: String,
        label: String = "",
        caption: String = "",
        color: String? = null,
        modifier: Modifier = Modifier.NONE,
    ) {
        components += Component.Kpi(value, label, caption, color, modifier)
    }

    /** Horizontal slider updating screen value [controls] locally on the device (no server round-trip). */
    fun slider(
        controls: String,
        step: Float? = null,
        color: String? = null,
        trackColor: String? = null,
        modifier: Modifier = Modifier.NONE,
    ) {
        components += Component.Slider(controls, step, color, trackColor, modifier)
    }

    /** Vertical container with card defaults (16 dp padding, 12 dp corner radius, surface background). */
    fun card(modifier: Modifier = Modifier.NONE, spacing: Int = 0, children: ComponentsBuilder.() -> Unit) {
        components += Component.Card(nested(children), spacing, modifier)
    }

    /** A tappable button; [action] is emitted as a Remote Compose host action ([ActionCodec]). */
    fun button(
        label: String,
        action: Action,
        variant: ButtonVariant = ButtonVariant.Filled,
        icon: String? = null,
        modifier: Modifier = Modifier.NONE,
    ) {
        components += Component.Button(label, action, variant, icon, modifier)
    }

    /**
     * Convenience for the wire form, e.g. `navigate:onboarding` or `open_url:https://example.com`
     * (grammar in [ActionCodec]). Unknown or malformed actions fail when the screen is built.
     */
    fun button(
        label: String,
        action: String,
        variant: ButtonVariant = ButtonVariant.Filled,
        icon: String? = null,
        modifier: Modifier = Modifier.NONE,
    ) {
        val decoded = requireNotNull(ActionCodec.decode(action)) {
            "$context, button '$label': unknown or malformed action '$action'. " +
                "Expected navigate:<screen>, open_url:<url>, reload:, track:<event> or custom:<payload>"
        }
        button(label, decoded, variant, icon, modifier)
    }

    /** Empty space of [height] dp along the parent's direction (a width inside a row). */
    fun spacer(height: Int = 8, modifier: Modifier = Modifier.NONE) {
        components += Component.Spacer(height, modifier)
    }

    /** A section that expands and collapses locally on the device without a server request (T-008). */
    fun expandable(
        title: String,
        expanded: Boolean = false,
        modifier: Modifier = Modifier.NONE,
        content: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Expandable(title = title, content = nested(content), expanded = expanded, modifier)
    }

    /** Children stacked vertically, [spacing] dp apart (T-012). */
    fun column(
        modifier: Modifier = Modifier.NONE,
        horizontalAlignment: Alignment = Alignment.Start,
        verticalArrangement: Arrangement = Arrangement.Start,
        spacing: Int = 0,
        collapsible: Boolean = false,
        children: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Column(
            nested(children),
            horizontalAlignment,
            verticalArrangement,
            spacing,
            collapsible,
            modifier,
        )
    }

    /** Children side by side, [spacing] dp apart (T-012). */
    fun row(
        modifier: Modifier = Modifier.NONE,
        horizontalArrangement: Arrangement = Arrangement.Start,
        verticalAlignment: Alignment = Alignment.Start,
        spacing: Int = 0,
        collapsible: Boolean = false,
        children: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Row(
            nested(children),
            horizontalArrangement,
            verticalAlignment,
            spacing,
            collapsible,
            modifier,
        )
    }

    /** Children stacked on top of each other, aligned in the box (T-012). */
    fun box(
        modifier: Modifier = Modifier.NONE,
        horizontalAlignment: Alignment = Alignment.Start,
        verticalAlignment: Alignment = Alignment.Start,
        children: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Box(nested(children), horizontalAlignment, verticalAlignment, modifier)
    }

    /** Shows the first child that fits within the available bounds (T-015). */
    fun fitBox(
        modifier: Modifier = Modifier.NONE,
        horizontalAlignment: Alignment = Alignment.Start,
        verticalAlignment: Alignment = Alignment.Start,
        children: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.FitBox(nested(children), horizontalAlignment, verticalAlignment, modifier)
    }

    /** Children laid out horizontally and wrapped onto new lines (T-014). */
    fun flow(
        modifier: Modifier = Modifier.NONE,
        horizontalAlignment: Alignment = Alignment.Start,
        verticalAlignment: Alignment = Alignment.Start,
        maxItemsInEachRow: Int? = null,
        maxLines: Int? = null,
        children: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Flow(
            nested(children),
            horizontalAlignment,
            verticalAlignment,
            maxItemsInEachRow,
            maxLines,
            modifier,
        )
    }

    /** Equal-width grid of [columns] columns, [spacing] dp apart (T-014). */
    fun grid(
        columns: Int,
        modifier: Modifier = Modifier.NONE,
        spacing: Int = 0,
        children: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Grid(columns, nested(children), spacing, modifier)
    }

    /** Horizontally scrolling row (T-014). */
    fun gallery(
        modifier: Modifier = Modifier.NONE,
        verticalAlignment: Alignment = Alignment.Start,
        spacing: Int = 8,
        children: ComponentsBuilder.() -> Unit,
    ) = row(
        modifier = modifier.copy(scroll = ScrollDirection.Horizontal),
        verticalAlignment = verticalAlignment,
        spacing = spacing,
        children = children,
    )

    /** [template] for each element of the array at [path], bound as `{{<alias>}}` and `{{$index}}`. */
    fun repeat(
        path: String,
        alias: String = "item",
        limit: Int? = null,
        empty: (ComponentsBuilder.() -> Unit)? = null,
        template: ComponentsBuilder.() -> Unit,
    ) = repeat(RepeatItems.Path(path), alias, limit, empty, template)

    /** [template] for each index of the parallel arrays in [zip] (name → path), bound as `{{<alias>.<name>}}`. */
    fun repeat(
        zip: Map<String, String>,
        alias: String = "item",
        limit: Int? = null,
        empty: (ComponentsBuilder.() -> Unit)? = null,
        template: ComponentsBuilder.() -> Unit,
    ) = repeat(RepeatItems.Zip(zip), alias, limit, empty, template)

    private fun repeat(
        items: RepeatItems,
        alias: String,
        limit: Int?,
        empty: (ComponentsBuilder.() -> Unit)?,
        template: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Repeat(
            items = items,
            alias = alias,
            limit = limit,
            template = nested(template),
            empty = empty?.let { nested(it) } ?: emptyList(),
        )
    }

    internal fun nested(block: ComponentsBuilder.() -> Unit): List<Component> =
        ComponentsBuilder(context).apply(block).components.toList()
}

/**
 * Kotlin DSL for one screen; builds a [ScreenDefinition] (the same model JSON files decode to).
 * Content is validated by [ScreenDefinition.validate] when the screen is added to the registry.
 */
@ScreenDslMarker
class ScreenBuilder internal constructor(val id: String) : ComponentsBuilder("Screen '$id'") {
    var title: String = id
    private val params = linkedMapOf<String, ParamSpec>()
    private val data = linkedMapOf<String, DataSource>()
    private val lookups = linkedMapOf<String, Map<String, String>>()
    private val values = linkedMapOf<String, ScreenValue>()
    private var error = emptyList<Component>()
    private var live: Live? = null

    /** Declares an on-device numeric value read as `{{values.<name>}}` and written by e.g. `slider(name)`. */
    fun value(name: String, default: Float = 0f, min: Float = 0f, max: Float = 100f) {
        val text = if (default % 1f == 0f) default.toInt().toString() else default.toString()
        values[name] = ScreenValue(default = text, min = min, max = max)
    }

    /** Declares an on-device numeric value whose [default] is a number or a `{{...}}` server binding. */
    fun value(name: String, default: String, min: Float = 0f, max: Float = 100f) {
        values[name] = ScreenValue(default = default, min = min, max = max)
    }

    /** Makes this a live screen (T-047, ADR 0029): apps receive its changes every [everyMs] ms. */
    fun live(everyMs: Int = Live.DEFAULT_EVERY_MS) {
        live = Live(everyMs)
    }

    /** A query parameter read as `{{params.<name>}}`; no [default] = required (HTTP 400 when missing). */
    fun param(name: String, default: String? = null) {
        params[name] = ParamSpec(default)
    }

    /** An HTTP JSON data source bound as `{{<name>.path}}` ([DataSource]). */
    fun data(
        name: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 5000,
        cacheSeconds: Int = 60,
        optional: Boolean = false,
    ) {
        data[name] = DataSource(url, headers, timeoutMs, cacheSeconds, optional)
    }

    /** A value table for `{{x | lookup:<name>}}`; key `"*"` is the fallback. */
    fun lookup(name: String, table: Map<String, String>) {
        lookups[name] = table
    }

    /** Shown instead of the components when a non-optional data source fails (may bind `{{params.x}}`). */
    fun error(block: ComponentsBuilder.() -> Unit) {
        error = nested(block)
    }

    internal fun build(): ScreenDefinition = ScreenDefinition(
        id,
        title,
        params.toMap(),
        data.toMap(),
        live,
        lookups.toMap(),
        values.toMap(),
        components.toList(),
        error,
    )
}
