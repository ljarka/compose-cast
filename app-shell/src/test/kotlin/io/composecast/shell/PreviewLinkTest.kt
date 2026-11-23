package io.composecast.shell

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreviewLinkTest {
    @Test
    fun `connect links carry an encoded http or https server`() {
        assertEquals(
            "https://app.example.com",
            PreviewLink.serverUrl("composecast://connect?url=https%3A%2F%2Fapp.example.com%2F"),
        )
        assertEquals(
            "http://10.0.2.2:8080",
            PreviewLink.serverUrl("composecast://connect?screen=main&url=http%3A%2F%2F10.0.2.2%3A8080"),
        )
        assertNull(PreviewLink.serverUrl("composecast://connect"))
        assertNull(PreviewLink.serverUrl("composecast://other?url=https%3A%2F%2Fa.com"))
        assertNull(PreviewLink.serverUrl("https://connect?url=https%3A%2F%2Fa.com"))
        assertNull(PreviewLink.serverUrl("composecast://connect?url=javascript%3Aalert(1)"))
        assertNull(PreviewLink.serverUrl(null))
    }

    @Test
    fun `typed addresses default to https and must be a plain server address`() {
        assertEquals("https://app.example.com", PreviewLink.normalize(" app.example.com/ "))
        assertEquals("http://192.168.1.10:8080", PreviewLink.normalize("http://192.168.1.10:8080"))
        assertEquals("https://example.com/cc", PreviewLink.normalize("https://example.com/cc"))
        assertNull(PreviewLink.normalize(""))
        assertNull(PreviewLink.normalize("ftp://example.com"))
        assertNull(PreviewLink.normalize("https://"))
        assertNull(PreviewLink.normalize("https://example.com/?x=1"))
        assertNull(PreviewLink.normalize("not a url at all"))
    }
}
