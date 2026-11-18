package io.composecast.server.sdk

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-026: image fetches must not reach loopback, private or link-local hosts, even via redirects. */
class ImageLoaderTest {
    private fun ip(literal: String) = InetAddress.getByName(literal) // literal: no DNS lookup

    @Test
    fun `private, loopback and link-local addresses are classified as private`() {
        listOf(
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "0.0.0.0",
            "224.0.0.1", "::1", "fd00::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1",
        ).forEach { assertTrue(HttpImageLoader.isPrivateAddress(ip(it)), it) }
    }

    @Test
    fun `public addresses are allowed`() {
        listOf("8.8.8.8", "1.1.1.1", "172.32.0.1", "2606:4700:4700::1111").forEach {
            assertFalse(HttpImageLoader.isPrivateAddress(ip(it)), it)
        }
    }

    @Test
    fun `a url on a private host is rejected before connecting`() {
        val loader = HttpImageLoader()
        listOf("http://127.0.0.1:8080/health", "http://169.254.169.254/latest/meta-data", "http://[::1]/a.png")
            .forEach { url ->
                val e = assertFailsWith<IllegalArgumentException>(url) { loader.load(url) }
                assertContains(e.message.orEmpty(), "private address")
            }
    }

    // A local server stands in for a public host: the test filter allows only 127.0.0.1, so the
    // redirect target (10.255.255.1, any private hop) must be rejected without being contacted.
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/image") {
            it.sendResponseHeaders(200, 3)
            it.responseBody.use { out -> out.write(byteArrayOf(1, 2, 3)) }
        }
        createContext("/to-image") { redirect(it, "/image") }
        createContext("/to-private") { redirect(it, "http://10.255.255.1/secret") }
        createContext("/loop") { redirect(it, "/loop") }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val loopbackOnly = HttpImageLoader(
        allowPrivateHosts = false,
        resolve = { host -> InetAddress.getAllByName(host).toList() },
        isAllowed = { it.isLoopbackAddress },
    )

    private fun redirect(exchange: com.sun.net.httpserver.HttpExchange, location: String) {
        exchange.responseHeaders.add("Location", location)
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun `allowed redirects are followed`() {
        assertContentEquals(byteArrayOf(1, 2, 3), loopbackOnly.load("$base/to-image"))
    }

    @Test
    fun `a redirect to a private host is rejected`() {
        val e = assertFailsWith<IllegalArgumentException> { loopbackOnly.load("$base/to-private") }
        assertContains(e.message.orEmpty(), "10.255.255.1")
        assertContains(e.message.orEmpty(), "redirect from")
    }

    @Test
    fun `more than three redirects fail`() {
        val e = assertFailsWith<IllegalStateException> { loopbackOnly.load("$base/loop") }
        assertContains(e.message.orEmpty(), "redirects")
    }

    @Test
    fun `the opt-out allows private hosts`() {
        assertContentEquals(byteArrayOf(1, 2, 3), HttpImageLoader(allowPrivateHosts = true).load("$base/image"))
    }
}
