package io.composecast.server.admin

import io.composecast.server.store.ComponentStore
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
    val components = ComponentStore(database)
    val password = "correct horse battery"

    /** Publishes straight into [screens]; set [failWith] to make the next publishes fail. */
    var failWith: String? = null

    /** What [publishing] was asked to preview. */
    val previews = mutableListOf<String>()
    val previewOwners = mutableListOf<String>()
    val updatedPreviews = mutableListOf<Pair<String, String>>()
    val stoppedPreviews = mutableListOf<String>()
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

        override fun preview(json: String, owner: String): String {
            failWith?.let { throw PublishException(it) }
            previews += json
            previewOwners += owner
            return "__preview-test${previews.size}"
        }

        override fun updatePreview(json: String, owner: String) {
            updatedPreviews += owner to json
        }

        override fun stopPreviews(owner: String) {
            stoppedPreviews += owner
        }

        override fun setHome(id: String) {
            failWith?.let { throw PublishException(it) }
            if (screens.live()[id] == null) throw PublishException("Screen '$id' must be published first")
            settings[SettingsStore.HOME_SCREEN] = id
        }

        override fun delete(id: String) {
            failWith?.let { throw PublishException(it) }
            val home = settings[SettingsStore.HOME_SCREEN]?.takeIf { it.isNotBlank() } ?: "main"
            if (id ==
                home
            ) {
                throw PublishException("Cannot remove the home screen '$id'; set another screen as home first")
            }
            screens.delete(id)
        }

        override fun describe(json: String): String {
            failWith?.let { throw PublishException(it) }
            return "HEADER v1.1.0\nROOT -2 VISIBLE"
        }

        override fun document(json: String): ByteArray {
            failWith?.let { throw PublishException(it) }
            return "rc:$json".toByteArray()
        }

        override fun publishComponent(id: String, json: String, by: String) {
            failWith?.let { throw PublishException(it) }
            components.publish(id, json, by)
        }

        override fun rollbackComponent(id: String, revisionId: Int, by: String) {
            failWith?.let { throw PublishException(it) }
            components.rollback(id, revisionId, by)
        }

        override fun deleteComponent(id: String) {
            failWith?.let { throw PublishException(it) }
            components.delete(id)
        }

        override fun componentDocument(json: String): ByteArray {
            failWith?.let { throw PublishException(it) }
            return "rc-component:$json".toByteArray()
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
        phoneRenderer: PhoneRenderer? = null,
        withComponents: Boolean = false,
        aiClient: ComponentAiClient = ComponentAiClient(),
        extra: Route.() -> Unit = {},
        test: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application {
            install(FreeMarker) { adminFreeMarker() }
            root = routing {
                adminRoutes(
                    auth,
                    screens,
                    settings,
                    publishing,
                    allowHttp,
                    probe = probe,
                    phoneRenderer = phoneRenderer,
                    components = components.takeIf { withComponents },
                    aiClient = aiClient,
                    extra = extra,
                )
            }
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
