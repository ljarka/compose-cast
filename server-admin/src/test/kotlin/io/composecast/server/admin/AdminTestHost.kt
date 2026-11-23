package io.composecast.server.admin

import io.composecast.server.store.ScreenStore
import io.composecast.server.store.SettingsStore
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
    val settings = SettingsStore(database)
    val password = "correct horse battery"

    /** Publishes straight into [screens]; set [failWith] to make the next publishes fail. */
    var failWith: String? = null

    /** What [publishing] was asked to preview. */
    val previews = mutableListOf<String>()
    val publishing = object : ScreenPublishing {
        override fun publish(id: String, json: String, by: String) {
            failWith?.let { throw PublishException(it) }
            screens.publish(id, json, by)
        }

        override fun rollback(id: String, revisionId: Int, by: String) {
            failWith?.let { throw PublishException(it) }
            screens.rollback(id, revisionId, by)
        }

        override fun publishBrand(brandJson: String) {
            failWith?.let { throw PublishException(it) }
            settings[SettingsStore.BRAND] = brandJson
        }

        override fun preview(json: String): String {
            failWith?.let { throw PublishException(it) }
            previews += json
            return "__preview-test${previews.size}"
        }

        override fun setHome(id: String) {
            failWith?.let { throw PublishException(it) }
            if (screens.live()[id] == null) throw PublishException("Screen '$id' must be published first")
            settings[SettingsStore.HOME_SCREEN] = id
        }
    }

    /** API answers for the wizard's probe by URL (no network); every host resolves to a public address. */
    val apiResponses = mutableMapOf<String, String>()
    val probe = ApiProbe(
        resolve = { listOf(java.net.InetAddress.getByName("93.184.216.34")) },
        send = { request ->
            val body = apiResponses[request.uri().toString()]
            if (body ==
                null
            ) {
                ApiProbe.Raw(404, emptyMap(), ByteArray(0))
            } else {
                ApiProbe.Raw(200, emptyMap(), body.toByteArray())
            }
        },
    )

    /** The routing tree, set when the application starts. */
    lateinit var root: RoutingNode

    fun run(
        allowHttp: Boolean = false,
        extra: Route.() -> Unit = {},
        test: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application {
            install(FreeMarker) { adminFreeMarker() }
            root = routing { adminRoutes(auth, screens, settings, publishing, allowHttp, probe = probe, extra = extra) }
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
