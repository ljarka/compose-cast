package io.composecast.android

import io.composecast.protocol.Headers
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ComposeCastClientTest {

    private fun fakeDecode(id: String, title: String, bytes: ByteArray, liveEveryMs: Int?) = ScreenDocument(
        id,
        title,
        bytes.size,
        rawDocument = null,
        liveEveryMs = liveEveryMs,
        contentHash = bytes.contentHashCode(),
    )

    private fun client(engine: MockEngine, cacheSize: Int = 32) = ComposeCastClient(
        config = ComposeCastConfig(serverUrl = "https://example.test", playerVersion = "1.2.3"),
        engine = engine,
        decode = ::fakeDecode,
        cacheSize = cacheSize,
    )

    @Test
    fun `getScreen decodes headers, params and reuses cached bytes on 304`() = runBlocking {
        var calls = 0
        val payload = byteArrayOf(1, 2, 3, 4)
        val engine = MockEngine { request ->
            calls++
            assertEquals("/screens/movie", request.url.encodedPath)
            assertEquals("42", request.url.parameters["id"])
            assertEquals("1.2.3", request.headers[Headers.PLAYER_VERSION])
            if (calls == 1) {
                assertNull(request.headers[HttpHeaders.IfNoneMatch])
                respond(
                    content = payload,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ETag to listOf("\"v1\""),
                        Headers.SCREEN_TITLE to listOf("Hello+World%21"),
                        Headers.LIVE to listOf("1000"),
                    ),
                )
            } else {
                assertEquals("\"v1\"", request.headers[HttpHeaders.IfNoneMatch])
                respond(content = ByteArray(0), status = HttpStatusCode.NotModified)
            }
        }
        val c = client(engine)
        val first = c.getScreen("movie", mapOf("id" to "42"))
        assertEquals("movie", first.id)
        assertEquals("Hello World!", first.title)
        assertEquals(4, first.sizeBytes)
        assertEquals(1000, first.liveEveryMs)

        val second = c.getScreen("movie", mapOf("id" to "42"))
        assertEquals("Hello World!", second.title)
        assertEquals(4, second.sizeBytes)
        assertEquals(1000, second.liveEveryMs)
        assertEquals(2, calls)
        c.close()
    }

    @Test
    fun `getScreen maps 426 Upgrade Required to ComposeCastUpgradeRequiredException`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":"Update app","minApiLevel":99}""",
                status = HttpStatusCode.UpgradeRequired,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val c = client(engine)
        val ex = assertFailsWith<ComposeCastUpgradeRequiredException> { c.getScreen("main") }
        assertEquals(426, ex.status)
        assertEquals(99, ex.minApiLevel)
        assertEquals("Update app", ex.message)
        c.close()
    }

    @Test
    fun `getScreen maps non-2xx to ComposeCastHttpException`() = runBlocking {
        val engine = MockEngine {
            respond(content = "missing", status = HttpStatusCode.NotFound)
        }
        val c = client(engine)
        val ex = assertFailsWith<ComposeCastHttpException> { c.getScreen("unknown") }
        assertEquals(404, ex.status)
        c.close()
    }

    @Test
    fun `cache evicts least recently used entry`() = runBlocking {
        val ifNoneMatchSeen = mutableListOf<String?>()
        val engine = MockEngine { request ->
            ifNoneMatchSeen += request.headers[HttpHeaders.IfNoneMatch]
            val id = request.url.encodedPath.substringAfterLast('/')
            respond(
                content = id.toByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ETag to listOf("\"etag-$id\"")),
            )
        }
        val c = client(engine, cacheSize = 2)
        c.getScreen("a")
        c.getScreen("b")
        c.getScreen("c") // evicts "a" -> cache holds [b, c]
        c.getScreen("c") // still cached -> sends If-None-Match
        c.getScreen("a") // was evicted -> no If-None-Match
        assertEquals(listOf(null, null, null, "\"etag-c\"", null), ifNoneMatchSeen)
        c.close()
    }

    @Test
    fun `listScreens and getBrand deserialize JSON responses`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/screens" -> respond(
                    content = """["main","detail"]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                "/brand" -> respond(
                    content = """{"name":"Demo","primaryColor":"#112233",""" +
                        """"secondaryColor":"#445566","homeScreen":"main"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> error("unexpected ${request.url}")
            }
        }
        val c = client(engine)
        assertEquals(listOf("main", "detail"), c.listScreens())
        val brand = c.getBrand()
        assertEquals("Demo", brand.name)
        assertEquals("main", brand.homeScreen)
        c.close()
    }
}
