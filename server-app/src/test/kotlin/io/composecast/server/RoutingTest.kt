package io.composecast.server

import io.composecast.protocol.Headers
import io.composecast.server.sdk.DataLoader
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.composeCast
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoutingTest {
    private val fakeImages = ImageLoader { _ ->
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }
    private val screens = LiveScreens(demoScreens(fakeImages))

    private fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(screens) }
        block()
    }

    @Test
    fun `If-None-Match with the current ETag answers 304`() = test {
        val first = client.get("/screens/main")
        assertEquals(HttpStatusCode.OK, first.status)
        val etag = assertNotNull(first.headers[HttpHeaders.ETag])
        assertEquals("no-cache", first.headers[HttpHeaders.CacheControl])

        val second = client.get("/screens/main") { header(HttpHeaders.IfNoneMatch, etag) }
        assertEquals(HttpStatusCode.NotModified, second.status)
        assertEquals(0, second.readRawBytes().size)

        val stale = client.get("/screens/main") { header(HttpHeaders.IfNoneMatch, "\"other\", W/$etag") }
        assertEquals(HttpStatusCode.NotModified, stale.status) // weak match counts for If-None-Match
        val changed = client.get("/screens/main") { header(HttpHeaders.IfNoneMatch, "\"other\"") }
        assertEquals(HttpStatusCode.OK, changed.status)
    }

    @Test
    fun `dynamic screen renders query params, including non-ASCII`() = test {
        for (word in listOf("hello", "zażółć gęślą jaźń")) {
            val response = client.get("/screens/echo") { parameter("word", word) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Echo", URLDecoder.decode(response.headers[Headers.SCREEN_TITLE], Charsets.UTF_8))
            assertTrue(word in String(response.readRawBytes(), Charsets.UTF_8), "'$word' not in document")
        }
        val etagA = client.get("/screens/echo") { parameter("word", "a") }.headers[HttpHeaders.ETag]
        val etagB = client.get("/screens/echo") { parameter("word", "b") }.headers[HttpHeaders.ETag]
        assertTrue(etagA != etagB)
    }

    @Test
    fun `missing param is 400, unknown screen is 404, both JSON`() = test {
        val missing = client.get("/screens/echo")
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertTrue("word" in missing.bodyAsText())
        val unknown = client.get("/screens/nope")
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        // 404s use protocol ErrorResponse (7e4d643); 400/502/500 still answer {"error": …}.
        assertTrue("\"code\":\"screen_not_found\"" in unknown.bodyAsText().replace(" ", ""))
    }

    @Test
    fun `50 concurrent requests succeed`() = test {
        val statuses = coroutineScope {
            (1..50).map { i ->
                async {
                    if (i % 2 == 0) {
                        client.get("/screens/echo") { parameter("word", "w$i") }.status
                    } else {
                        client.get("/screens/main").status
                    }
                }
            }.awaitAll()
        }
        assertEquals(List(50) { HttpStatusCode.OK }, statuses)
    }

    @Test
    fun `bound screen - data and params in the document, 502 when the source fails, 400 for a missing param`() = test {
        var failing = false
        val api = DataLoader { request ->
            check(!failing) { "HTTP 500" }
            """{"name":"${request.url.substringAfter("q=")}"}"""
        }
        screens.replace(
            composeCast(fakeImages, api) {
                screen("search") {
                    param("q")
                    data("api", url = "https://api.dev/search?q={{params.q}}", cacheSeconds = 0)
                    text("Found {{api.name}}")
                }
            },
        )
        val ok = client.get("/screens/search") { parameter("q", "dune") }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue("Found dune" in String(ok.readRawBytes(), Charsets.UTF_8))

        assertEquals(HttpStatusCode.BadRequest, client.get("/screens/search").status)

        failing = true
        val down = client.get("/screens/search") { parameter("q", "dune") }
        assertEquals(HttpStatusCode.BadGateway, down.status)
        assertTrue("data source 'api' failed: HTTP 500" in down.bodyAsText(), down.bodyAsText())
    }

    @Test
    fun `a replaced registry is served from the next request`() = test {
        assertTrue("echo" in client.get("/screens").bodyAsText())
        screens.replace(composeCast(fakeImages) { screen("only") { text("New") } })
        assertEquals(HttpStatusCode.NotFound, client.get("/screens/main").status)
        assertEquals(HttpStatusCode.OK, client.get("/screens/only").status)
    }
}
