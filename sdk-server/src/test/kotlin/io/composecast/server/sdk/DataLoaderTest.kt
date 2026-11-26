package io.composecast.server.sdk

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DataLoaderTest {

    @Test
    fun `rejects loopback and cloud metadata addresses by default without leaking URL`() {
        val loader = HttpDataLoader(
            allowPrivateHosts = false,
            resolve = { host ->
                when (host) {
                    "metadata.test" -> listOf(InetAddress.getByName("169.254.169.254"))
                    else -> listOf(InetAddress.getByName("127.0.0.1"))
                }
            },
            isAllowed = { !HttpImageLoader.isPrivateAddress(it) },
        )
        val url = "http://metadata.test/latest?token=secret-value"
        val ex = assertFailsWith<IllegalArgumentException> { loader.load(DataRequest(url)) }
        assertFalse(ex.message.orEmpty().contains("secret-value"), "error must not leak URL/secrets")
    }

    @Test
    fun `rejects redirect to private host and allows when allowPrivateHosts is true`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:$port/secret")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/secret") { exchange ->
            val body = """{"ok":true}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            // Allow initial public-looking host, reject 127.0.0.1 on the redirect hop.
            var hops = 0
            val blocking = HttpDataLoader(
                allowPrivateHosts = false,
                resolve = { listOf(InetAddress.getByName("127.0.0.1")) },
                isAllowed = { hops++ == 0 },
            )
            assertFailsWith<IllegalArgumentException> {
                blocking.load(DataRequest("http://127.0.0.1:$port/start"))
            }

            val permissive = HttpDataLoader(allowPrivateHosts = true)
            assertEquals("""{"ok":true}""", permissive.load(DataRequest("http://127.0.0.1:$port/start")))
        } finally {
            server.stop(0)
        }
    }
}
