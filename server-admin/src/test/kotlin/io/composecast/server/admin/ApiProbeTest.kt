package io.composecast.server.admin

import java.net.InetAddress
import java.net.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** T-031: the probe fetches public APIs only and never sends a request to a private address. */
class ApiProbeTest {
    private val addresses = mapOf(
        "api.example.com" to "93.184.216.34",
        "internal.example.com" to "10.0.0.5",
        "localhost" to "127.0.0.1",
        "metadata.example.com" to "169.254.169.254",
    )
    private val sent = mutableListOf<String>()

    private fun probe(vararg answers: Pair<String, ApiProbe.Raw>, env: Map<String, String> = emptyMap()) = ApiProbe(
        env = env::get,
        resolve = { host -> listOf(InetAddress.getByName(addresses.getValue(host))) },
        send = { request: HttpRequest ->
            sent += request.uri().toString() + request.headers().firstValue("X-Key").orElse("")
            answers.toMap().getValue(request.uri().toString())
        },
    )

    private fun json(body: String, status: Int = 200) =
        ApiProbe.Raw(status, mapOf("content-type" to "application/json"), body.toByteArray())

    @Test
    fun `a public API is fetched and parsed`() {
        val result = probe(
            "https://api.example.com/shows" to json("""[{"id": 1}]"""),
        ).probe("https://api.example.com/shows")
        assertEquals(200, result.status)
        assertNull(result.error)
        assertNotNull(result.json)
    }

    @Test
    fun `private, loopback and link-local hosts are refused before any request`() {
        for (url in listOf(
            "http://internal.example.com/x",
            "http://localhost:8080/admin",
            "http://metadata.example.com/latest",
        )) {
            val result = probe().probe(url)
            assertContains(result.error!!, "private or local address")
        }
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `a redirect to a private host is refused`() {
        val redirect = ApiProbe.Raw(302, mapOf("location" to "http://internal.example.com/secret"), ByteArray(0))
        val result = probe("https://api.example.com/go" to redirect).probe("https://api.example.com/go")
        assertContains(result.error!!, "internal.example.com is a private")
        assertEquals(listOf("https://api.example.com/go"), sent)
    }

    @Test
    fun `secrets come from the environment, errors and non-JSON are explained`() {
        val p = probe("https://api.example.com/a" to json("{}"), env = mapOf("COMPOSECAST_SECRET_KEY" to "s3cr3t"))
        p.probe("https://api.example.com/a", mapOf("X-Key" to "{{secret.KEY}}"))
        assertEquals(listOf("https://api.example.com/as3cr3t"), sent)
        assertContains(
            p.probe("https://api.example.com/a", mapOf("X-Key" to "{{secret.OTHER}}")).error!!,
            "COMPOSECAST_SECRET_OTHER",
        )

        assertEquals(
            "the API answered HTTP 404",
            probe("https://api.example.com/x" to json("{}", 404)).probe("https://api.example.com/x").error,
        )
        assertEquals(
            "the response is not JSON",
            probe("https://api.example.com/h" to json("<html>")).probe("https://api.example.com/h").error,
        )
        val big = json("[" + "1,".repeat(ApiProbe.MAX_BYTES) + "1]")
        assertContains(
            probe("https://api.example.com/b" to big).probe("https://api.example.com/b").error!!,
            "larger than 2 MB",
        )
        assertContains(probe().probe("ftp://api.example.com/x").error!!, "only http")
    }
}
