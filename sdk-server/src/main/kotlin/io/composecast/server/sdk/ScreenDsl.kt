package io.composecast.server.sdk

import io.composecast.protocol.Actions

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
    data class Button(val label: String, val action: String) : ScreenNode
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

    /**
     * A tappable button. [action] uses the `type:payload` convention, e.g. `navigate:onboarding`
     * or `open_url:https://example.com`; it is emitted as a Remote Compose host action.
     */
    fun button(label: String, action: String) {
        val type = action.substringBefore(':')
        require(type in KNOWN_ACTIONS) {
            "Screen '$id', button '$label': unknown action type '$type' in '$action'. " +
                "Allowed: ${KNOWN_ACTIONS.joinToString()}"
        }
        nodes += ScreenNode.Button(label, action)
    }

    fun spacer(height: Int = 8) {
        require(height >= 0) { "Screen '$id': spacer height must be >= 0, was $height" }
        nodes += ScreenNode.Spacer(height)
    }

    internal fun nodes(): List<ScreenNode> = nodes.toList()

    private companion object {
        val KNOWN_ACTIONS = setOf(Actions.NAVIGATE, Actions.OPEN_URL, Actions.TRACK, Actions.CUSTOM)
    }
}
