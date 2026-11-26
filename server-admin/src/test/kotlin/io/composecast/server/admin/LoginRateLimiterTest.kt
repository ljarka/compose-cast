package io.composecast.server.admin

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoginRateLimiterTest {

    @Test
    fun `limits after maxFailures per key and resets on success or window expiry`() {
        var now = Instant.parse("2026-11-26T12:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = now
        }
        val limiter = LoginRateLimiter(maxFailures = 2, window = Duration.ofMinutes(5), clock = clock)

        assertFalse(limiter.isLimited("1.1.1.1"))
        limiter.recordFailure("1.1.1.1")
        assertFalse(limiter.isLimited("1.1.1.1"))
        limiter.recordFailure("1.1.1.1")
        assertTrue(limiter.isLimited("1.1.1.1"))
        assertFalse(limiter.isLimited("2.2.2.2"), "other IPs are unaffected")

        now = now.plus(Duration.ofMinutes(6))
        assertFalse(limiter.isLimited("1.1.1.1"), "expired window clears limit")

        limiter.recordFailure("2.2.2.2")
        limiter.recordFailure("2.2.2.2")
        assertTrue(limiter.isLimited("2.2.2.2"))
        limiter.recordSuccess("2.2.2.2")
        assertFalse(limiter.isLimited("2.2.2.2"))
    }

    @Test
    fun `admin login returns 429 after repeated failures and sets CSP header`() {
        val host = AdminTestHost()
        host.auth.bootstrapAdmin("owner@example.com", host.password)
        host.run { client ->
            val page = client.get("/admin/login")
            val csp = assertNotNull(page.headers["Content-Security-Policy"])
            assertContains(csp, "frame-ancestors 'none'")

            repeat(10) {
                val res = client.submitForm(
                    url = "/admin/login",
                    formParameters = parameters {
                        append("email", "owner@example.com")
                        append("password", "wrong-password-123")
                    },
                ) { header("X-Forwarded-For", "198.51.100.7") }
                assertEquals(HttpStatusCode.Unauthorized, res.status)
            }
            val blocked = client.submitForm(
                url = "/admin/login",
                formParameters = parameters {
                    append("email", "owner@example.com")
                    append("password", host.password)
                },
            ) { header("X-Forwarded-For", "198.51.100.7") }
            assertEquals(HttpStatusCode.TooManyRequests, blocked.status)

            val otherIp = client.submitForm(
                url = "/admin/login",
                formParameters = parameters {
                    append("email", "owner@example.com")
                    append("password", host.password)
                },
            ) { header("X-Forwarded-For", "198.51.100.8") }
            assertEquals(HttpStatusCode.SeeOther, otherIp.status)
        }
    }
}
