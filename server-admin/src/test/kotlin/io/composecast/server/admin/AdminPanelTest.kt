package io.composecast.server.admin

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.delete
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-007 A2: login, logout, sessions and the role policy, through the Ktor test host. */
class AdminPanelTest {
    private val host = AdminTestHost()
    private val auth = host.auth
    private val password = host.password

    /** Stand-ins for future mutating routes: the policy must cover any route added inside `loggedIn`. */
    private val extra: io.ktor.server.routing.Route.() -> Unit = {
        post("/test") { call.respondText("changed") }
        put("/test") { call.respondText("changed") }
        patch("/test") { call.respondText("changed") }
        delete("/test") { call.respondText("changed") }
    }

    private fun admin(allowHttp: Boolean = false, test: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        host.run(allowHttp, extra, test)

    /** Mutating routes in the routing tree (method, path with `x` for parameters), minus the public ones. */
    private fun mutating() = host.root.getAllRoutes().mapNotNull(::methodAndPath)
        .filter { (method, path) -> method !in setOf(HttpMethod.Get, HttpMethod.Head) && path !in PUBLIC }

    @Test
    fun `admin pages redirect to the login page without a session`() = admin { client ->
        val response = client.get("/admin")
        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/admin/login", response.headers[HttpHeaders.Location])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(HttpStatusCode.OK, client.get("/admin/login").status)
    }

    @Test
    fun `the env admin logs in, sees the panel and logs out`() = admin { client ->
        assertTrue(auth.bootstrapAdmin(" Owner@Example.com ", password))
        val login = client.login("owner@example.com", password)
        assertEquals(HttpStatusCode.SeeOther, login.status)
        assertEquals("/admin", login.headers[HttpHeaders.Location])
        val setCookie = login.headers[HttpHeaders.SetCookie]!!
        listOf("Path=/admin", "HttpOnly", "Secure", "SameSite=Strict", "Max-Age=604800").forEach {
            assertContains(setCookie, it, ignoreCase = true)
        }

        val cookie = login.sessionCookie()
        assertEquals(
            "/admin/templates", // no screens yet: start from a template (T-030)
            client.get("/admin") {
                header(HttpHeaders.Cookie, cookie)
            }.headers[HttpHeaders.Location],
        )
        val home = client.get("/admin/screens") { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.OK, home.status)
        assertContains(home.bodyAsText(), "owner@example.com (admin)")
        assertEquals(
            "/admin",
            client.get("/admin/login") {
                header(HttpHeaders.Cookie, cookie)
            }.headers[HttpHeaders.Location],
        )

        val logout = client.request("/admin/logout") {
            method = HttpMethod.Post
            header(HttpHeaders.Cookie, cookie)
        }
        assertEquals("/admin/login", logout.headers[HttpHeaders.Location])
        val afterLogout = client.get("/admin") { header(HttpHeaders.Cookie, cookie) }
        assertEquals(
            HttpStatusCode.SeeOther,
            afterLogout.status,
            "the old cookie no longer works (deleted on the server)",
        )
    }

    @Test
    fun `a wrong password or unknown email shows an error`() = admin { client ->
        auth.bootstrapAdmin("owner@example.com", password)
        for ((email, pass) in listOf("owner@example.com" to "wrong password!", "nobody@example.com" to password)) {
            val response = client.login(email, pass)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertContains(response.bodyAsText(), "Wrong email or password.")
            assertFalse(response.headers.contains(HttpHeaders.SetCookie))
        }
        assertEquals(HttpStatusCode.Unauthorized, client.login("owner@example.com", "x".repeat(200)).status)
    }

    @Test
    fun `a viewer gets 403 on every mutating route, an admin gets through`() = admin { client ->
        auth.createUser("viewer@example.com", password, Role.VIEWER)
        auth.createUser("admin@example.com", password, Role.ADMIN)
        val viewer = client.login("viewer@example.com", password).sessionCookie()
        val admin = client.login("admin@example.com", password).sessionCookie()
        val mutating = mutating()
        assertTrue(mutating.size >= 8, "the 4 stand-ins + screen and settings routes; found: $mutating")

        val screens = client.get("/admin/screens") { header(HttpHeaders.Cookie, viewer) }
        assertEquals(HttpStatusCode.OK, screens.status, "viewers read")
        for ((method, path) in mutating) {
            suspend fun call(cookie: String?) = client.request(path) {
                this.method = method
                cookie?.let { header(HttpHeaders.Cookie, it) }
            }.status
            assertEquals(HttpStatusCode.Unauthorized, call(null), "$method $path without a session")
            assertEquals(HttpStatusCode.Forbidden, call(viewer), "$method $path as a viewer")
            // Editors may be refused per route (settings are admin-only); an admin never is.
            val asAdmin = call(admin)
            assertTrue(
                asAdmin != HttpStatusCode.Unauthorized && asAdmin != HttpStatusCode.Forbidden,
                "$method $path as an admin: $asAdmin",
            )
        }
        val logout = client.request("/admin/logout") {
            method = HttpMethod.Post
            header(HttpHeaders.Cookie, viewer)
        }
        assertEquals(HttpStatusCode.SeeOther, logout.status, "a viewer can still log out")
    }

    @Test
    fun `plain http from another host explains that https is needed`() = admin { client ->
        auth.bootstrapAdmin("owner@example.com", password)
        val plain = client.login("owner@example.com", password, host = "admin.example.com")
        assertEquals(HttpStatusCode.BadRequest, plain.status)
        assertContains(plain.bodyAsText(), "needs HTTPS")

        val proxied = client.login("owner@example.com", password, host = "admin.example.com", forwardedProto = "https")
        assertEquals(HttpStatusCode.SeeOther, proxied.status, "behind the HTTPS proxy")
    }

    @Test
    fun `allowHttp drops the Secure flag`() = admin(allowHttp = true) { client ->
        auth.bootstrapAdmin("owner@example.com", password)
        val login = client.login("owner@example.com", password, host = "192.168.1.10")
        assertEquals(HttpStatusCode.SeeOther, login.status)
        assertFalse(login.headers[HttpHeaders.SetCookie]!!.contains("Secure", ignoreCase = true))
    }

    @Test
    fun `user input is HTML-escaped`() = admin { client ->
        val body = client.login("\"><script>alert(1)</script>", "whatever password").bodyAsText()
        assertFalse("<script>alert" in body)
        assertContains(body, "&lt;script&gt;")
    }

    @Test
    fun `the first admin is created once, with a real password`() {
        assertFailsWith<IllegalArgumentException> { auth.bootstrapAdmin("owner@example.com", "short") }
        assertFailsWith<IllegalArgumentException> { auth.bootstrapAdmin("not-an-email", password) }
        assertTrue(auth.bootstrapAdmin("owner@example.com", password))
        assertFalse(auth.bootstrapAdmin("someone@example.com", password), "accounts exist")
        assertEquals(null, auth.login("someone@example.com", password))
    }

    private companion object {
        /** Mutating routes that work without a session or for every role. */
        val PUBLIC = setOf("/admin/login", "/admin/logout")

        fun methodAndPath(node: RoutingNode): Pair<HttpMethod, String>? {
            val method = (node.selector as? HttpMethodRouteSelector)?.method ?: return null
            val segments = generateSequence(node) { it.parent }.toList().reversed()
                .mapNotNull {
                    when (val selector = it.selector) {
                        is PathSegmentConstantRouteSelector -> selector.value
                        is PathSegmentParameterRouteSelector -> "x"
                        else -> null
                    }
                }
            return method to segments.joinToString("/", prefix = "/")
        }
    }
}
