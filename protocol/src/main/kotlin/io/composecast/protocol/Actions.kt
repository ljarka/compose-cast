package io.composecast.protocol

/** Wire type names of [Action]s, used by [ActionCodec]. Elsewhere use the [Action] types directly. */
public object Actions {
    /** Navigate to a server-driven screen. */
    public const val NAVIGATE: String = "navigate"

    /** Open a URL in the system browser or the app registered for it. */
    public const val OPEN_URL: String = "open_url"

    /** Reload the current screen from the server. */
    public const val RELOAD: String = "reload"

    /** Fire an analytics event; the app decides where to send it. */
    public const val TRACK: String = "track"

    /** Host-defined behavior; payload is passed through unchanged. */
    public const val CUSTOM: String = "custom"

    /** On-device: toggle animated visibility of a target group. */
    public const val TOGGLE: String = "toggle"

    /** On-device: show a target visibility group. */
    public const val SHOW: String = "show"

    /** On-device: hide a target visibility group. */
    public const val HIDE: String = "hide"
}

/**
 * Draft previews (ADR 0027, 0030): screen ids `__preview-<128-bit hex>`, served only while an
 * admin keeps the preview open. Apps mark them as previews; real screen ids never use the prefix.
 */
public object PreviewIds {
    public const val PREFIX: String = "__preview-"

    public fun isPreview(id: String): Boolean = id.startsWith(PREFIX)
}

/** HTTP request/response header names used by the Compose Cast protocol. */
public object Headers {
    /** SDK version sent by the player on every `/screens/{id}` request; used for compatibility checks. */
    public const val PLAYER_VERSION: String = "X-Composecast-Player-Version"

    /** Player's `CoreDocument.DOCUMENT_API_LEVEL` (T-004). */
    public const val RC_API_LEVEL: String = "X-Composecast-Rc-Api-Level"

    /** Bitmask of Remote Compose profiles supported by the player (T-004). */
    public const val RC_PROFILES: String = "X-Composecast-Rc-Profiles"

    /** Screen title sent with `GET /screens/{id}`; UTF-8, percent-encoded (URL encoding). */
    public const val SCREEN_TITLE: String = "X-Composecast-Screen-Title"

    /**
     * Sent with `GET /screens/{id}` of a live screen (T-047, ADR 0029): its `everyMs`. The app then
     * opens `/screens/{id}/live` (WebSocket, same query and headers) to receive updates.
     */
    public const val LIVE: String = "X-Composecast-Live"
}
