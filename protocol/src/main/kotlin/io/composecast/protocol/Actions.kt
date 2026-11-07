package io.composecast.protocol

object Actions {
    const val NAVIGATE = "navigate"
    const val OPEN_URL = "open_url"
    const val TRACK = "track"
    const val CUSTOM = "custom"
}

object Headers {
    const val PLAYER_VERSION = "X-Composecast-Player-Version"
    const val BRAND_ETAG = "X-Composecast-Brand-ETag"

    /** Screen title sent with `GET /screens/{id}`; UTF-8, percent-encoded (URL encoding). */
    const val SCREEN_TITLE = "X-Composecast-Screen-Title"
}

object Variables {
    const val USER_NAME = "user_name"
    const val CART_COUNT = "cart_count"
}
