package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.Component
import io.composecast.protocol.DataSource
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
    fun text(text: String, style: String = "body") {
        components += Component.Text(text, TextStyle.fromKey(style))
    }

    fun image(url: String, alt: String = "") {
        components += Component.Image(url, alt)
    }

    /** A tappable button; [action] is emitted as a Remote Compose host action ([ActionCodec]). */
    fun button(label: String, action: Action) {
        components += Component.Button(label, action)
    }

    /**
     * Convenience for the wire form, e.g. `navigate:onboarding` or `open_url:https://example.com`
     * (grammar in [ActionCodec]). Unknown or malformed actions fail when the screen is built.
     */
    fun button(label: String, action: String) {
        val decoded = requireNotNull(ActionCodec.decode(action)) {
            "$context, button '$label': unknown or malformed action '$action'. " +
                "Expected navigate:<screen>, open_url:<url>, reload:, track:<event> or custom:<payload>"
        }
        button(label, decoded)
    }

    /** Vertical space in dp. */
    fun spacer(height: Int = 8) {
        components += Component.Spacer(height)
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
