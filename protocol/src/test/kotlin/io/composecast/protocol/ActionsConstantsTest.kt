package io.composecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ActionsConstantsTest {

    @Test
    fun `Actions constants match wire format`() {
        assertEquals("navigate", Actions.NAVIGATE)
        assertEquals("open_url", Actions.OPEN_URL)
        assertEquals("reload", Actions.RELOAD)
        assertEquals("track", Actions.TRACK)
        assertEquals("custom", Actions.CUSTOM)
    }

    @Test
    fun `Headers constants match wire format`() {
        assertEquals("X-Composecast-Player-Version", Headers.PLAYER_VERSION)
        assertEquals("X-Composecast-Rc-Api-Level", Headers.RC_API_LEVEL)
        assertEquals("X-Composecast-Rc-Profiles", Headers.RC_PROFILES)
        assertEquals("X-Composecast-Screen-Title", Headers.SCREEN_TITLE)
    }
}
