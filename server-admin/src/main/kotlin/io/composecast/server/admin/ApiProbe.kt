package io.composecast.server.admin

import io.composecast.server.sdk.HttpDataLoader
import io.composecast.server.sdk.HttpImageLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches an API for the data source wizard (T-031), on the server (ADR 0008: keys stay there).
 * Like images (T-026), no private, loopback or link-local hosts unless [allowPrivateHosts]: every
 * redirect hop is resolved and checked before it is followed. At most [MAX_BYTES] are read.
 * `{{secret.NAME}}` in a header is `COMPOSECAST_SECRET_NAME` from [env].
 */
internal class ApiProbe(
    private val allowPrivateHosts: Boolean = false,
    private val env: (String) -> String? = System::getenv,
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val send: (HttpRequest) -> Raw = ::fetch,
) {
    /** An HTTP answer with at most [MAX_BYTES] + 1 body bytes read. */
    class Raw(val status: Int, val headers: Map<String, String>, val body: ByteArray)

    /** A probe's outcome: the parsed JSON, or why there is none. */
    data class Result(
        val status: Int?,
        val contentType: String?,
        val millis: Long,
        val json: JsonElement?,
        val error: String?,
    )

    fun probe(url: String, headers: Map<String, String> = emptyMap()): Result {
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000
        return try {
            var target = URI(url.trim())
            var response: Raw
            var hops = 0
            while (true) {
                check(target.scheme == "http" || target.scheme == "https") { "only http:// and https:// addresses" }
                checkHost(target)
                val request = HttpRequest.newBuilder(
                    target,
                ).timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                headers.forEach { (name, value) -> request.header(name, secrets(value)) }
                response = send(request.GET().build())
                val location = response.headers["location"]
                if (response.status !in REDIRECTS || location == null) break
                check(++hops <= 5) { "too many redirects" }
                target = target.resolve(location)
            }
            val type = response.headers["content-type"]
            if (response.status !in 200..299) {
                return Result(response.status, type, elapsed(), null, "the API answered HTTP ${response.status}")
            }
            check(response.body.size <= MAX_BYTES) { "the response is larger than ${MAX_BYTES / 1024 / 1024} MB" }
            val json = runCatching { Json.parseToJsonElement(String(response.body, Charsets.UTF_8)) }
                // An object or an array: a bare word such as <html> is not an API response.
                .getOrNull()?.takeIf { it is JsonObject || it is JsonArray }
            Result(response.status, type, elapsed(), json, if (json == null) "the response is not JSON" else null)
        } catch (e: Exception) {
            Result(null, null, elapsed(), null, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun checkHost(uri: URI) {
        val host = requireNotNull(uri.host) { "no host in the address" }
        if (allowPrivateHosts) return
        val blocked = resolve(host).firstOrNull { HttpImageLoader.isPrivateAddress(it) }
        check(blocked == null) { "$host is a private or local address; only public APIs can be probed" }
    }

    /** `{{secret.NAME}}` → the environment's `COMPOSECAST_SECRET_NAME` (missing → an error naming it). */
    private fun secrets(value: String) = SECRET.replace(value) { match ->
        val name = match.groupValues[1]
        env("COMPOSECAST_SECRET_$name") ?: error("secret $name is not set (COMPOSECAST_SECRET_$name in .env)")
    }

    companion object {
        /**
         * The screens' own limit (`HttpDataLoader.MAX_BYTES`, 2 MB): what a screen can load, the wizard
         * can look at. (T-031 said 256 KB; TVMaze's `/shows` alone is 373 KB.)
         */
        const val MAX_BYTES = HttpDataLoader.MAX_BYTES
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val SECRET = Regex("""\{\{\s*secret\.([A-Za-z0-9_]+)\s*}}""")
        private val client: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER) // each hop is checked above
            .connectTimeout(Duration.ofSeconds(5))
            .build()

        private fun fetch(request: HttpRequest): Raw {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val body = response.body().use { it.readNBytes(MAX_BYTES + 1) }
            val headers = response.headers().map().mapNotNull { (k, v) ->
                v.firstOrNull()?.let { k.lowercase() to it }
            }.toMap()
            return Raw(response.statusCode(), headers, body)
        }
    }
}
