package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec
import io.composecast.protocol.Component
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.TextStyle

@DslMarker
annotation class ScreenDslMarker

/**
 * Kotlin DSL for one screen; builds a [ScreenDefinition] (the same model JSON files decode to).
 * Content is validated by [ScreenDefinition.validate] when the screen is added to the registry.
 */
@ScreenDslMarker
class ScreenBuilder internal constructor(val id: String) {
    var title: String = id
    private val components = mutableListOf<Component>()

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
            "Screen '$id', button '$label': unknown or malformed action '$action'. " +
                "Expected navigate:<screen>, open_url:<url>, reload:, track:<event> or custom:<payload>"
        }
        button(label, decoded)
    }

    /** Vertical space in dp. */
    fun spacer(height: Int = 8) {
        components += Component.Spacer(height)
    }

    internal fun build(): ScreenDefinition = ScreenDefinition(id, title, components.toList())
}
