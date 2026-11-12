package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.Alignment
import io.composecast.protocol.Arrangement
import io.composecast.protocol.Component
import io.composecast.protocol.DataSource
import io.composecast.protocol.Modifier
import io.composecast.protocol.ParamSpec
import io.composecast.protocol.RepeatItems
import io.composecast.protocol.ScreenDefinition
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
    fun text(text: String, style: String = "body", modifier: Modifier = Modifier.NONE) {
        components += Component.Text(text, TextStyle.fromKey(style), modifier)
    }

    fun image(url: String, alt: String = "", modifier: Modifier = Modifier.NONE) {
        components += Component.Image(url, alt, modifier)
    }

    /** A tappable button; [action] is emitted as a Remote Compose host action ([ActionCodec]). */
    fun button(label: String, action: Action, modifier: Modifier = Modifier.NONE) {
        components += Component.Button(label, action, modifier)
    }

    /**
     * Convenience for the wire form, e.g. `navigate:onboarding` or `open_url:https://example.com`
     * (grammar in [ActionCodec]). Unknown or malformed actions fail when the screen is built.
     */
    fun button(label: String, action: String, modifier: Modifier = Modifier.NONE) {
        val decoded = requireNotNull(ActionCodec.decode(action)) {
            "$context, button '$label': unknown or malformed action '$action'. " +
                "Expected navigate:<screen>, open_url:<url>, reload:, track:<event> or custom:<payload>"
        }
        button(label, decoded, modifier)
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
        children: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Column(nested(children), horizontalAlignment, verticalArrangement, spacing, modifier)
    }

    /** Children side by side, [spacing] dp apart (T-012). */
    fun row(
        modifier: Modifier = Modifier.NONE,
        horizontalArrangement: Arrangement = Arrangement.Start,
        verticalAlignment: Alignment = Alignment.Start,
        spacing: Int = 0,
        children: ComponentsBuilder.() -> Unit,
    ) {
        components += Component.Row(nested(children), horizontalArrangement, verticalAlignment, spacing, modifier)
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
    private var error = emptyList<Component>()

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

    internal fun build(): ScreenDefinition =
        ScreenDefinition(id, title, params.toMap(), data.toMap(), lookups.toMap(), components.toList(), error)
}
