package io.composecast.android

import io.composecast.protocol.Headers
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeCastClientTest {

    private fun fakeDecode(id: String, title: String, bytes: ByteArray, liveEveryMs: Int?) = ScreenDocument(
        id,
        title,
        bytes.size,
        rawDocument = null,
        liveEveryMs = liveEveryMs,
        contentHash = bytes.contentHashCode(),
    )

    private fun client(
        engine: MockEngine,
        cacheSize: Int = 32,
        headers: suspend () -> Map<String, String> = { emptyMap() },
    ) = ComposeCastClient(
        config = ComposeCastConfig(
            serverUrl = "https://example.test",
            playerVersion = "1.2.3",
            headers = headers,
            cacheSize = cacheSize,
        ),
        engine = engine,
        decode = ::fakeDecode,
    )

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

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

    @Test
    fun `custom headers reach every REST endpoint but never override protocol headers`() = runBlocking {
        val seen = mutableListOf<io.ktor.http.Headers>()
        val engine = MockEngine { request ->
            seen += request.headers
            when (request.url.encodedPath) {
                "/screens" -> respond("""["main"]""", HttpStatusCode.OK, json)
                "/brand" -> respond(
                    """{"name":"D","primaryColor":"#112233","secondaryColor":"#445566","homeScreen":"main"}""",
                    HttpStatusCode.OK,
                    json,
                )
                else -> respond(byteArrayOf(1), HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"v1\""))
            }
        }
        var token = 0
        val c = client(engine) {
            token++
            mapOf(
                "Authorization" to "Bearer t$token",
                "x-composecast-player-version" to "evil", // reserved, any case
                HttpHeaders.IfNoneMatch to "\"forged\"",
            )
        }
        c.getScreen("main")
        c.getScreen("main") // cached: If-None-Match must be the real ETag
        c.listScreens()
        c.getBrand()
        assertEquals(listOf("Bearer t1", "Bearer t2", "Bearer t3", "Bearer t4"), seen.map { it["Authorization"] })
        seen.forEach { assertEquals(listOf("1.2.3"), it.getAll(Headers.PLAYER_VERSION)) }
        assertNull(seen[0][HttpHeaders.IfNoneMatch])
        assertEquals(listOf("\"v1\""), seen[1].getAll(HttpHeaders.IfNoneMatch))
        c.close()
    }

    @Test
    fun `cacheSize 0 never revalidates and a negative cacheSize is rejected`() = runBlocking {
        val ifNoneMatchSeen = mutableListOf<String?>()
        val engine = MockEngine { request ->
            ifNoneMatchSeen += request.headers[HttpHeaders.IfNoneMatch]
            respond(byteArrayOf(1), HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"v1\""))
        }
        client(engine, cacheSize = 0).use { c ->
            c.getScreen("main")
            c.getScreen("main")
        }
        assertEquals(listOf<String?>(null, null), ifNoneMatchSeen)
        assertFailsWith<IllegalArgumentException> { ComposeCastConfig(serverUrl = "https://x.test", cacheSize = -1) }
        Unit
    }

    @Test
    fun `client is Closeable and unusable after use`() = runBlocking {
        val engine = MockEngine { respond(byteArrayOf(1), HttpStatusCode.OK) }
        val c = client(engine)
        val closeable: Closeable = c
        closeable.use { assertEquals(1, c.getScreen("main").sizeBytes) }
        assertFails { c.getScreen("main") }
        Unit
    }

    @Test
    fun `errors map to ComposeCastError`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/screens/old" -> respond(
                    """{"error":"Update app","minApiLevel":99}""",
                    HttpStatusCode.UpgradeRequired,
                    json,
                )
                "/screens/missing" -> respond("missing", HttpStatusCode.NotFound)
                "/screens" -> respond("boom", HttpStatusCode.InternalServerError)
                else -> throw IOException("connection refused")
            }
        }
        val c = client(engine)
        suspend fun errorOf(block: suspend () -> Unit) = assertFails { block() }.toComposeCastError()

        assertEquals(ComposeCastError.UpgradeRequired(99, "Update app"), errorOf { c.getScreen("old") })
        val notFound = errorOf { c.getScreen("missing") }
        assertIs<ComposeCastError.Http>(notFound)
        assertEquals(404, notFound.statusCode)
        val listFailed = errorOf { c.listScreens() } // was a raw Ktor exception before T-058
        assertIs<ComposeCastError.Http>(listFailed)
        assertEquals(500, listFailed.statusCode)
        val unreachable = errorOf { c.getScreen("offline") }
        assertIs<ComposeCastError.Network>(unreachable)
        assertEquals("connection refused", unreachable.message)
        c.close()
    }

    @Test
    fun `liveScreen sends custom and protocol headers on the WebSocket`() = runBlocking {
        val received = CompletableDeferred<io.ktor.http.Headers>()
        val server = embeddedServer(Netty, port = 0) {
            install(io.ktor.server.websocket.WebSockets)
            routing {
                webSocket("/screens/{id}/live") {
                    received.complete(call.request.headers)
                    send(Frame.Binary(true, byteArrayOf(7, 8)))
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "done"))
                }
            }
        }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val c = ComposeCastClient(
                config = ComposeCastConfig(
                    serverUrl = "http://127.0.0.1:$port",
                    playerVersion = "1.2.3",
                    headers = { mapOf("Authorization" to "Bearer live", Headers.RC_API_LEVEL to "0") },
                ),
                engine = null, // the real OkHttp engine, as in apps
                decode = ::fakeDecode,
            )
            val frames = withTimeout(10_000) { c.liveScreen("dash").toList() } // completes on CANNOT_ACCEPT
            assertEquals(listOf(2), frames.map { it.sizeBytes })
            val headers = received.await()
            assertEquals("Bearer live", headers["Authorization"])
            assertEquals("1.2.3", headers[Headers.PLAYER_VERSION])
            assertEquals(1, headers.getAll(Headers.RC_API_LEVEL)?.size)
            assertTrue(headers[Headers.RC_API_LEVEL] != "0")
            c.close()
        } finally {
            server.stop(0, 0)
        }
    }
}
