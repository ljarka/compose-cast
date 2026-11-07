package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.ActionCodec

@DslMarker
annotation class ScreenDslMarker

/** Text styles supported by [ScreenBuilder.text]. */
enum class TextStyle(val key: String) {
    H1("h1"),
    H2("h2"),
    H3("h3"),
    Body("body"),
    Caption("caption"),
    ;

    companion object {
        fun fromKey(key: String): TextStyle =
            entries.firstOrNull { it.key == key }
                ?: throw IllegalArgumentException(
                    "Unknown text style '$key'. Allowed: ${entries.joinToString { it.key }}",
                )
    }
}

/**
 * Authoring model of a screen. It is not sent over the wire: [RemoteComposeRenderer]
 * turns it into a Remote Compose binary document.
 */
internal sealed interface ScreenNode {
    data class Text(val text: String, val style: TextStyle) : ScreenNode
    data class Image(val url: String, val alt: String) : ScreenNode
    data class Button(val label: String, val action: Action) : ScreenNode
    data class Spacer(val height: Int) : ScreenNode
}

@ScreenDslMarker
class ScreenBuilder internal constructor(val id: String) {
    var title: String = id
    private val nodes = mutableListOf<ScreenNode>()

    fun text(text: String, style: String = "body") {
        nodes += ScreenNode.Text(text, TextStyle.fromKey(style))
    }

    fun image(url: String, alt: String = "") {
        nodes += ScreenNode.Image(url, alt)
    }

    /** A tappable button; [action] is emitted as a Remote Compose host action ([ActionCodec]). */
    fun button(label: String, action: Action) {
        val encoded = ActionCodec.encode(action)
        require(encoded.length <= ActionCodec.MAX_ENCODED_LENGTH) {
            "Screen '$id', button '$label': encoded action is ${encoded.length} characters, " +
                "Remote Compose allows ${ActionCodec.MAX_ENCODED_LENGTH}"
        }
        nodes += ScreenNode.Button(label, action)
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

    fun spacer(height: Int = 8) {
        require(height >= 0) { "Screen '$id': spacer height must be >= 0, was $height" }
        nodes += ScreenNode.Spacer(height)
    }

    internal fun nodes(): List<ScreenNode> = nodes.toList()
}
