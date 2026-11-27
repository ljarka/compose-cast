package io.composecast.server

import io.composecast.protocol.UpgradeRequiredResponse
import io.composecast.server.admin.AdminAuth
import io.composecast.server.admin.CachingPhoneRenderer
import io.composecast.server.admin.HttpPhoneRenderer
import io.composecast.server.admin.PhoneRenderer
import io.composecast.server.admin.ScreenPublishing
import io.composecast.server.admin.adminPanel
import io.composecast.server.sdk.DataSourceException
import io.composecast.server.sdk.HttpDataLoader
import io.composecast.server.sdk.HttpImageLoader
import io.composecast.server.sdk.ScreenParameterException
import io.composecast.server.sdk.UnsupportedClientVersionException
import io.composecast.server.store.ComponentStore
import io.composecast.server.store.ScreenStore
import io.composecast.server.store.SettingsStore
import io.composecast.server.store.UserStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * Configuration (environment variables):
 * - `PORT` (default 8080)
 * - `COMPOSECAST_DATA_DIR`: serve `screens/<id>.json` (+ optional `brand.json`) from this directory
 *   instead of the built-in demo screens (layout: ADR 0011).
 * - `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS=true`: allow image URLs on loopback/private/link-local
 *   hosts (local development only; default false, T-026).
 * - `COMPOSECAST_RELOAD=false`: do not reload the data directory when its files change (default:
 *   reload, polled every 2 s; T-042). Files mode only.
 * - `COMPOSECAST_MODE`: `files` (default: serve `COMPOSECAST_DATA_DIR` read-only) or `admin`
 *   (T-007: `compose-cast.db` in `COMPOSECAST_DATA_DIR` is the source of truth; the first start
 *   imports the directory's screens and brand; the directory must be writable).
 * - Admin mode only: `COMPOSECAST_ADMIN_EMAIL` / `COMPOSECAST_ADMIN_PASSWORD` create the first
 *   admin account when there is none (required on the first start; ignored afterwards).
 *   `COMPOSECAST_ADMIN_ALLOW_HTTP=true`: the login cookie is not `Secure` (local testing without
 *   TLS; default false: the panel at `/admin` needs HTTPS or localhost).
 *   `COMPOSECAST_PREVIEW_RENDERER_URL` (e.g. `http://preview-renderer:8090`): the optional
 *   `preview-renderer` service (T-048); the screen editor then shows the screen in a phone frame.
 */
fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    // Built before the server starts: an invalid screen stops startup with a clear message.
    val allowPrivateImageHosts = System.getenv("COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS").toBoolean()
    val imageLoader = HttpImageLoader(allowPrivateHosts = allowPrivateImageHosts)
    val dataLoader = HttpDataLoader(allowPrivateHosts = allowPrivateImageHosts)
    val dataDir = System.getenv("COMPOSECAST_DATA_DIR")?.let(Path::of)
    var admin: AdminPanelConfig? = null
    var healthCheck: () -> Boolean = { true }
    var onStop: () -> Unit = {}
    val screens = when (val mode = System.getenv("COMPOSECAST_MODE") ?: "files") {
        "files" -> LiveScreens(
            loadScreens(dataDir, imageLoader = imageLoader, dataLoader = dataLoader),
        ).also { screens ->
            if (dataDir != null && System.getenv("COMPOSECAST_RELOAD")?.toBoolean() != false) {
                val reloader = DataDirReloader(
                    dataDir,
                    screens,
                    load = { loadScreens(it, imageLoader = imageLoader, dataLoader = dataLoader) },
                )
                reloader.start()
                onStop = reloader::stop
            }
        }
        "admin" -> {
            requireNotNull(dataDir) { "COMPOSECAST_MODE=admin needs COMPOSECAST_DATA_DIR (the database lives there)" }
            require(Files.isWritable(dataDir)) {
                "COMPOSECAST_MODE=admin writes ${AdminScreens.DATABASE_FILE} into COMPOSECAST_DATA_DIR ($dataDir), " +
                    "which is not writable: in Docker drop ':ro' from the volume and run " +
                    "'sudo chown -R 1000:1000 <data dir>'"
            }
            val database = AdminScreens.openDatabase(dataDir)
            healthCheck = database::ping
            val adminScreens = AdminScreens(
                ScreenStore(database),
                SettingsStore(database),
                imageLoader,
                dataLoader = dataLoader,
                components = ComponentStore(database),
            )
            adminScreens.importIfEmpty(dataDir)
            val userStore = UserStore(database)
            userStore.deleteExpiredSessions()
            val auth = AdminAuth(userStore)
            auth.bootstrapFromEnv(System.getenv("COMPOSECAST_ADMIN_EMAIL"), System.getenv("COMPOSECAST_ADMIN_PASSWORD"))
            LiveScreens(adminScreens.load(), initialHome = adminScreens.homeScreen()).also { live ->
                admin = AdminPanelConfig(
                    auth,
                    ScreenStore(database),
                    SettingsStore(database),
                    AdminPublishing(adminScreens, live),
                    components = ComponentStore(database),
                    allowHttp = System.getenv("COMPOSECAST_ADMIN_ALLOW_HTTP").toBoolean(),
                    // The wizard's API probe follows the image rule (T-026): private hosts only for local development.
                    allowPrivateApiHosts = allowPrivateImageHosts,
                    phoneRenderer = System.getenv("COMPOSECAST_PREVIEW_RENDERER_URL")?.takeIf { it.isNotBlank() }
                        ?.let { CachingPhoneRenderer(HttpPhoneRenderer(it)) },
                    pruneExpiredSessions = userStore::deleteExpiredSessions,
                )
            }
        }
        else -> error("COMPOSECAST_MODE must be 'files' or 'admin', was '$mode'")
    }
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(screens, admin, healthCheck = healthCheck, onStop = onStop)
    }.start(wait = true)
}

/** The admin panel of admin mode (T-007); `null` in files mode, where `/admin` does not exist. */
class AdminPanelConfig(
    val auth: AdminAuth,
    val screens: ScreenStore,
    val settings: SettingsStore,
    val publishing: ScreenPublishing,
    val allowHttp: Boolean = false,
    val allowPrivateApiHosts: Boolean = false,
    val phoneRenderer: PhoneRenderer? = null,
    /** Custom components page (T-049); null = not shown. */
    val components: ComponentStore? = null,
    /** Periodically removes expired admin sessions (T-053); null in tests without a background loop. */
    val pruneExpiredSessions: (() -> Int)? = null,
)

fun Application.module(
    screens: LiveScreens,
    admin: AdminPanelConfig? = null,
    healthCheck: () -> Boolean = { true },
    onStop: () -> Unit = {},
) {
    monitor.subscribe(ApplicationStopping) { onStop() }
    install(CallLogging)
    installLiveScreens()
    install(ContentNegotiation) {
        json(Json { prettyPrint = true })
    }
    install(StatusPages) {
        // A dynamic screen without a required query parameter: the client's fault.
        exception<ScreenParameterException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
        }
        // An external API failed and the screen has no `error` components (T-006); already logged.
        exception<DataSourceException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to cause.message))
        }
        // Client player RC API level is too old for the requested screen (T-004).
        exception<UnsupportedClientVersionException> { call, cause ->
            call.respond(
                HttpStatusCode.UpgradeRequired,
                UpgradeRequiredResponse(cause.message ?: "App update required", cause.minApiLevel),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error for ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "internal error"))
        }
    }
    configureRouting(screens, healthCheck)
    admin?.let {
        adminPanel(
            it.auth,
            it.screens,
            it.settings,
            it.publishing,
            it.allowHttp,
            it.allowPrivateApiHosts,
            it.phoneRenderer,
            it.components,
        )
        it.pruneExpiredSessions?.let { prune ->
            launch {
                while (isActive) {
                    delay(1.hours)
                    runCatching { withContext(Dispatchers.IO) { prune() } }
                        .onFailure { e -> environment.log.warn("Failed to prune expired sessions", e) }
                }
            }
        }
    }
}
