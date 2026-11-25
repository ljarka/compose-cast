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

    /** On-device: toggle animated visibility of a target group. */
    const val TOGGLE = "toggle"

    /** On-device: show a target visibility group. */
    const val SHOW = "show"

    /** On-device: hide a target visibility group. */
    const val HIDE = "hide"
}

/**
 * Draft previews (ADR 0027, 0030): screen ids `__preview-<128-bit hex>`, served only while an
 * admin keeps the preview open. Apps mark them as previews; real screen ids never use the prefix.
 */
object PreviewIds {
    const val PREFIX = "__preview-"

    fun isPreview(id: String): Boolean = id.startsWith(PREFIX)
}

/** HTTP request/response header names used by the Compose Cast protocol. */
object Headers {
    /** SDK version sent by the player on every `/screens/{id}` request; used for compatibility checks. */
    const val PLAYER_VERSION = "X-Composecast-Player-Version"

    /** Player's `CoreDocument.DOCUMENT_API_LEVEL` (T-004). */
    const val RC_API_LEVEL = "X-Composecast-Rc-Api-Level"

    /** Bitmask of Remote Compose profiles supported by the player (T-004). */
    const val RC_PROFILES = "X-Composecast-Rc-Profiles"

    /** Screen title sent with `GET /screens/{id}`; UTF-8, percent-encoded (URL encoding). */
    const val SCREEN_TITLE = "X-Composecast-Screen-Title"

    /**
     * Sent with `GET /screens/{id}` of a live screen (T-047, ADR 0029): its `everyMs`. The app then
     * opens `/screens/{id}/live` (WebSocket, same query and headers) to receive updates.
     */
    const val LIVE = "X-Composecast-Live"
}
