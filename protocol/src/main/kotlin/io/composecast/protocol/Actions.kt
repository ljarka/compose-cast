package io.composecast.protocol

/** Wire type names of [Action]s, used by [ActionCodec]. Elsewhere use the [Action] types directly. */
object Actions {
    /** Navigate to a server-driven screen. */
    const val NAVIGATE = "navigate"
    /** Open a URL in the system browser or the app registered for it. */
    const val OPEN_URL = "open_url"
    /** Reload the current screen from the server. */
    const val RELOAD = "reload"
    /** Fire an analytics event; the app decides where to send it. */
    const val TRACK = "track"
    /** Host-defined behavior; payload is passed through unchanged. */
    const val CUSTOM = "custom"
}

/** HTTP request/response header names used by the Compose Cast protocol. */
object Headers {
    /** SDK version sent by the player on every `/screens/{id}` request; used for compatibility checks. */
    const val PLAYER_VERSION = "X-Composecast-Player-Version"

    /** Screen title sent with `GET /screens/{id}`; UTF-8, percent-encoded (URL encoding). */
    const val SCREEN_TITLE = "X-Composecast-Screen-Title"
}
