package io.composecast.protocol

/** Wire type names of [Action]s, used by [ActionCodec]. Elsewhere use the [Action] types. */
object Actions {
    const val NAVIGATE = "navigate"
    const val OPEN_URL = "open_url"
    const val RELOAD = "reload"
    const val TRACK = "track"
    const val CUSTOM = "custom"
}

object Headers {
    const val PLAYER_VERSION = "X-Composecast-Player-Version"

    /** Screen title sent with `GET /screens/{id}`; UTF-8, percent-encoded (URL encoding). */
    const val SCREEN_TITLE = "X-Composecast-Screen-Title"
}
