package io.composecast.server.admin

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Admin-only user management (`/admin/users`): list, create, change role, reset password, delete. */
class UsersPagesTest {
    private val host = AdminTestHost()
    private val auth = host.auth

    private suspend fun HttpClient.postForm(cookie: String, path: String, vararg fields: Pair<String, String>) =
        submitForm(
            path,
            parameters { fields.forEach { (k, v) -> append(k, v) } },
        ) { header(HttpHeaders.Cookie, cookie) }

    @Test
    fun `only admins can view or change users and see the Users menu item`() = host.run { client ->
        val adminCookie = with(host) { client.loginAs(Role.ADMIN) }
        val editorCookie = with(host) { client.loginAs(Role.EDITOR) }
        val viewerCookie = with(host) { client.loginAs(Role.VIEWER) }

        val adminScreens = client.get("/admin/screens") { header(HttpHeaders.Cookie, adminCookie) }.bodyAsText()
        assertContains(adminScreens, """href="/admin/users"""")
        val editorScreens = client.get("/admin/screens") { header(HttpHeaders.Cookie, editorCookie) }.bodyAsText()
        assertFalse("""href="/admin/users"""" in editorScreens)
        val viewerScreens = client.get("/admin/screens") { header(HttpHeaders.Cookie, viewerCookie) }.bodyAsText()
        assertFalse("""href="/admin/users"""" in viewerScreens)

        assertEquals(HttpStatusCode.OK, client.get("/admin/users") { header(HttpHeaders.Cookie, adminCookie) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/admin/users") { header(HttpHeaders.Cookie, editorCookie) }.status,
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/admin/users") { header(HttpHeaders.Cookie, viewerCookie) }.status,
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.postForm(
                editorCookie,
                "/admin/users",
                "email" to "new@example.com",
                "role" to "viewer",
                "password" to "temporary-pass-123",
            ).status,
        )
    }

    @Test
    fun `admin creates a user, changes role, resets password and deletes account`() = host.run { client ->
        val adminCookie = with(host) { client.loginAs(Role.ADMIN) }

        // Create a new editor account
        val created = client.postForm(
            adminCookie,
            "/admin/users",
            "email" to " Alice@Example.com ",
            "role" to "editor",
            "password" to "temp-password-123",
        )
        assertEquals(HttpStatusCode.SeeOther, created.status)
        assertEquals("/admin/users?created", created.headers[HttpHeaders.Location])

        val alice = auth.findByEmail("alice@example.com")
        assertNotNull(alice)
        assertEquals(Role.EDITOR, alice.role)
        assertNull(alice.lastLoginAt)

        // Duplicate email and short password are rejected with 422
        val dup = client.postForm(
            adminCookie,
            "/admin/users",
            "email" to "alice@example.com",
            "role" to "viewer",
            "password" to "temp-password-123",
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, dup.status)
        assertContains(dup.bodyAsText(), "already exists")

        val shortPw = client.postForm(
            adminCookie,
            "/admin/users",
            "email" to "bob@example.com",
            "role" to "viewer",
            "password" to "short",
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, shortPw.status)
        assertContains(shortPw.bodyAsText(), "at least 12 characters")

        // Alice logs in -> lastLoginAt is recorded and visible on /admin/users
        val aliceLogin = client.login("alice@example.com", "temp-password-123")
        assertEquals(HttpStatusCode.SeeOther, aliceLogin.status)
        val aliceCookie = aliceLogin.sessionCookie()
        assertNotNull(auth.findByEmail("alice@example.com")!!.lastLoginAt)

        val listPage = client.get("/admin/users") { header(HttpHeaders.Cookie, adminCookie) }.bodyAsText()
        assertContains(listPage, "alice@example.com")

        // Change Alice's role to viewer
        val roleChanged = client.postForm(adminCookie, "/admin/users/${alice.id}/role", "role" to "viewer")
        assertEquals(HttpStatusCode.SeeOther, roleChanged.status)
        assertEquals(Role.VIEWER, auth.findUser(alice.id)!!.role)

        // Reset Alice's password -> old session is revoked, new password works
        val pwReset = client.postForm(
            adminCookie,
            "/admin/users/${alice.id}/password",
            "password" to "brand-new-password-99",
        )
        assertEquals(HttpStatusCode.SeeOther, pwReset.status)
        assertEquals(
            HttpStatusCode.SeeOther,
            client.get("/admin/screens") { header(HttpHeaders.Cookie, aliceCookie) }.status,
            "old session revoked after password reset",
        )
        assertEquals(HttpStatusCode.Unauthorized, client.login("alice@example.com", "temp-password-123").status)
        assertEquals(HttpStatusCode.SeeOther, client.login("alice@example.com", "brand-new-password-99").status)

        // Delete Alice
        val deleted = client.postForm(adminCookie, "/admin/users/${alice.id}/delete")
        assertEquals(HttpStatusCode.SeeOther, deleted.status)
        assertNull(auth.findUser(alice.id))
    }

    @Test
    fun `the last admin cannot be demoted or deleted`() = host.run { client ->
        val adminCookie = with(host) { client.loginAs(Role.ADMIN) }
        val onlyAdmin = auth.listUsers().single()

        val demote = client.postForm(adminCookie, "/admin/users/${onlyAdmin.id}/role", "role" to "editor")
        assertEquals(HttpStatusCode.UnprocessableEntity, demote.status)
        assertContains(demote.bodyAsText(), "last admin")
        assertEquals(Role.ADMIN, auth.findUser(onlyAdmin.id)!!.role)

        val remove = client.postForm(adminCookie, "/admin/users/${onlyAdmin.id}/delete")
        assertEquals(HttpStatusCode.UnprocessableEntity, remove.status)
        assertContains(remove.bodyAsText(), "last admin")
        assertNotNull(auth.findUser(onlyAdmin.id))
    }
}
