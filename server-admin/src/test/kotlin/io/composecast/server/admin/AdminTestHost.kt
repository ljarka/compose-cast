package io.composecast.server.admin

import io.composecast.server.store.ScreenStore
import io.composecast.server.store.StoreDatabase
import io.composecast.server.store.UserStore
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.install
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.assertEquals

/** A temp database and the admin routes on the Ktor test host (no real port, no browser). */
internal class AdminTestHost {
    private val database = StoreDatabase.open(Files.createTempDirectory("composecast-admin").resolve("compose-cast.db"))
    val auth = AdminAuth(UserStore(database), bcryptCost = 4) // the minimum: fast tests
    val screens = ScreenStore(database)
    val password = "correct horse battery"

    /** The routing tree, set when the application starts. */
    lateinit var root: RoutingNode

    fun run(
        allowHttp: Boolean = false,
        extra: Route.() -> Unit = {},
        test: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application {
            install(FreeMarker) { adminFreeMarker() }
            root = routing { adminRoutes(auth, screens, allowHttp, extra) }
        }
        startApplication()
        test(createClient { followRedirects = false })
    }

    /** Creates the account and returns its session cookie (`name=value`). */
    suspend fun HttpClient.loginAs(role: Role): String {
        val email = "${role.id}@example.com"
        auth.createUser(email, password, role)
        return login(email, password).sessionCookie()
    }
}

internal suspend fun HttpClient.login(
    email: String,
    password: String,
    host: String? = null,
    forwardedProto: String? = null,
) = submitForm(
    "/admin/login",
    parameters {
        append("email", email)
        append("password", password)
    },
) {
    host?.let { header(HttpHeaders.Host, it) }
    forwardedProto?.let { header("X-Forwarded-Proto", it) }
}

internal fun HttpResponse.sessionCookie(): String {
    assertEquals(HttpStatusCode.SeeOther, status, "login failed")
    return headers[HttpHeaders.SetCookie]!!.substringBefore(';')
}
