package io.composecast.server.sdk

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches image bytes (PNG, JPEG, GIF, BMP) when screens are rendered.
 *
 * Images are embedded in the Remote Compose document instead of being referenced by URL:
 * in remote-compose 1.0.0-alpha20 the player rejects URL images by default, checks them against
 * a 1x1 size declared by the creation DSL, and would fetch them synchronously on the main thread.
 */
public fun interface ImageLoader {
    public fun load(url: String): ByteArray
}

/**
 * Default [ImageLoader]: HTTP(S) GET with timeouts. Image URLs may come from JSON files, the admin
 * panel, request parameters (`{{params.img}}` is bound unencoded) or external API responses, so:
 *
 * - only `http`/`https` URLs, at most [MAX_BYTES];
 * - **no private hosts** (T-026, SSRF): the host is resolved before connecting and the URL is
 *   rejected if *any* address is loopback, any-local, site-local, link-local (incl. cloud metadata
 *   `169.254.169.254`), multicast or IPv6 ULA `fc00::/7` — see [isPrivateAddress];
 * - redirects are followed by hand, at most [MAX_REDIRECTS], and every hop is checked the same way.
 *
 * [allowPrivateHosts] disables the address check (local development:
 * `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS=true` in `server-app`).
 *
 * Residual risk, accepted: DNS rebinding. The host is resolved for the check and again by the HTTP
 * client when it connects, so a hostile DNS server can answer with a public address first and a
 * private one second. Pinning the checked address would need a custom socket layer.
 */
public class HttpImageLoader internal constructor(
    private val allowPrivateHosts: Boolean,
    private val resolve: (String) -> List<InetAddress>,
    private val isAllowed: (InetAddress) -> Boolean,
) : ImageLoader {
    public constructor(allowPrivateHosts: Boolean = false) :
        this(allowPrivateHosts, { host -> InetAddress.getAllByName(host).toList() }, { !isPrivateAddress(it) })

    override fun load(url: String): ByteArray {
        var uri = URI.create(url)
        repeat(MAX_REDIRECTS + 1) {
            checkTarget(uri, url)
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                val status = response.statusCode()
                if (status in REDIRECT_CODES) {
                    val location = response.headers().firstValue("Location").orElse(null)
                        ?: error("GET $uri returned HTTP $status without Location")
                    uri = uri.resolve(location)
                    return@repeat
                }
                check(status in 200..299) { "GET $uri returned HTTP $status" }
                val bytes = body.readNBytes(MAX_BYTES + 1)
                check(bytes.size <= MAX_BYTES) { "image $url is larger than ${MAX_BYTES / 1024 / 1024} MB" }
                return bytes
            }
        }
        error("image $url: more than $MAX_REDIRECTS redirects")
    }

    private fun checkTarget(uri: URI, original: String) {
        require(uri.scheme == "http" || uri.scheme == "https") { "only http/https image URLs are allowed: $uri" }
        val host = requireNotNull(uri.host) { "image URL without host: $uri" }
        if (allowPrivateHosts) return
        val addresses = resolve(host.removeSurrounding("[", "]"))
        val blocked = addresses.firstOrNull { !isAllowed(it) }
        require(blocked == null) {
            "image host $host resolves to private address ${blocked?.hostAddress}" +
                (if (uri.toString() != original) " (redirect from $original)" else "") +
                "; set COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS=true for local development"
        }
    }

    public companion object {
        public const val MAX_BYTES: Int = 5 * 1024 * 1024
        public const val MAX_REDIRECTS: Int = 3
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        // Shared by all instances; redirects are handled in load() so every hop is checked.
        private val client: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        /**
         * True for addresses the server must not fetch images from: loopback, any-local, site-local
         * (10/8, 172.16/12, 192.168/16, fec0::/10), link-local (169.254/16, fe80::/10), multicast,
         * IPv6 ULA (fc00::/7) and IPv4-mapped IPv6 forms of those.
         */
        public fun isPrivateAddress(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isSiteLocalAddress ||
                address.isLinkLocalAddress || address.isMulticastAddress
            ) {
                return true
            }
            if (address is Inet6Address) {
                val bytes = address.address
                if ((bytes[0].toInt() and 0xFE) == 0xFC) return true // fc00::/7 unique local
                // ::ffff:a.b.c.d (Java usually returns Inet4Address for these, but be safe).
                val mapped =
                    bytes.take(10).all { it == 0.toByte() } && bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
                if (mapped) return isPrivateAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
            }
            return false
        }
    }
}
