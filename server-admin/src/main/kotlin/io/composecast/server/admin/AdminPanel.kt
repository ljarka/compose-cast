package io.composecast.server.admin

import freemarker.cache.ClassTemplateLoader
import freemarker.core.HTMLOutputFormat
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import io.composecast.server.store.ComponentStore
import io.composecast.server.store.ScreenStore
import io.composecast.server.store.SettingsStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Mounts the admin panel at `/admin` (T-007): Freemarker pages, a session cookie scoped to `/admin`.
 * Every route inside [loggedIn] needs a session; a `viewer` gets 403 on every mutating method.
 *
 * [allowHttp]: the cookie is `Secure` unless true, so browsers keep it only over HTTPS (and on
 * `localhost`). Set it only for local development without TLS.
 */
fun Application.adminPanel(
    auth: AdminAuth,
    screens: ScreenStore,
    settings: SettingsStore,
    publishing: ScreenPublishing,
    allowHttp: Boolean = false,
    allowPrivateApiHosts: Boolean = false,
    phoneRenderer: PhoneRenderer? = null,
    components: ComponentStore? = null,
) {
    install(FreeMarker) { adminFreeMarker() }
    routing {
        adminRoutes(
            auth,
            screens,
            settings,
            publishing,
            allowHttp,
            ApiProbe(allowPrivateHosts = allowPrivateApiHosts),
            phoneRenderer = phoneRenderer,
            components = components,
        )
    }
}

internal fun Configuration.adminFreeMarker() {
    templateLoader = ClassTemplateLoader(AdminAuth::class.java.classLoader, "templates")
    outputFormat = HTMLOutputFormat.INSTANCE // ${…} is HTML-escaped in every template
    defaultEncoding = "UTF-8"
    urlEscapingCharset = "UTF-8" // ${id?url} fails without it
    templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
}

/** The cookie value: an opaque token (the database stores its hash). */
@Serializable
internal data class AdminSession(val token: String)

internal const val SESSION_COOKIE = "composecast_admin"

/** [extra] adds routes behind the login (tests use it to check the role policy on mutating routes). */
internal fun Route.adminRoutes(
    auth: AdminAuth,
    screens: ScreenStore,
    settings: SettingsStore,
    publishing: ScreenPublishing,
    allowHttp: Boolean,
    probe: ApiProbe = ApiProbe(),
    phoneRenderer: PhoneRenderer? = null,
    components: ComponentStore? = null,
    rateLimiter: LoginRateLimiter = LoginRateLimiter(),
    aiClient: ComponentAiClient = ComponentAiClient(),
    extra: Route.() -> Unit = {},
) {
    phoneRenderer?.let { application.attributes.put(PhoneRendererKey, it) } // T-048: editors show the phone frame
    components?.let { application.attributes.put(ComponentStoreKey, it) } // T-049: palette "My components"
    application.attributes.put(SettingsStoreKey, settings)
    route("/admin") {
        install(SecurityHeaders)
        install(Sessions) {
            cookie<AdminSession>(SESSION_COOKIE) {
                cookie.path = "/admin"
                cookie.httpOnly = true
                cookie.secure = !allowHttp
                cookie.maxAgeInSeconds = auth.sessionTtl.seconds
                // Never sent with cross-site requests: no CSRF through the browser's cookie.
                cookie.extensions["SameSite"] = "Strict"
            }
        }

        get("/login") {
            if (call.currentUser(auth) != null) return@get call.seeOther("/admin")
            call.loginPage(error = call.insecureTransportError(allowHttp))
        }
        post("/login") {
            val form = call.receiveParameters()
            val email = form["email"].orEmpty()
            call.insecureTransportError(allowHttp)?.let {
                return@post call.loginPage(it, email, HttpStatusCode.BadRequest)
            }
            val clientIp = call.clientIp()
            if (rateLimiter.isLimited(clientIp)) {
                return@post call.loginPage(
                    "Too many failed login attempts. Try again later.",
                    email,
                    HttpStatusCode.TooManyRequests,
                )
            }
            val password = form["password"].orEmpty()
            val login = withContext(Dispatchers.Default) { auth.login(email, password) }
            if (login == null) {
                rateLimiter.recordFailure(clientIp)
                return@post call.loginPage("Wrong email or password.", email, HttpStatusCode.Unauthorized)
            }
            rateLimiter.recordSuccess(clientIp)
            call.sessions.set(AdminSession(login.token))
            call.seeOther("/admin")
        }
        post("/logout") {
            call.sessions.get<AdminSession>()?.let { session ->
                auth.user(session.token)?.let { publishing.stopPreviews(it.previewOwner) } // T-032
                auth.logout(session.token)
            }
            call.sessions.clear<AdminSession>()
            call.seeOther("/admin/login")
        }

        loggedIn(auth) {
            // An app without screens starts at the templates (T-030): a working app in one click.
            get { call.seeOther(if (screens.isEmpty()) "/admin/templates" else "/admin/screens") }
            screenPages(screens, settings, publishing)
            visualPages(screens, probe, publishing)
            settingsPages(settings, publishing)
            usersPages(auth)
            templatesPages(screens, settings, publishing)
            phoneRenderer?.let { phonePreview(screens, publishing, it) }
            components?.let { componentPages(it, settings, publishing, phoneRenderer, aiClient) }
            extra()
        }
    }
}

/** The template model every logged-in page starts from (`_layout.ftl`: title, user, role, canEdit). */
internal fun ApplicationCall.page(title: String): Map<String, Any> = adminUser.let {
    mapOf(
        "title" to title,
        "user" to it,
        "role" to it.role.id,
        "canEdit" to it.role.canEdit,
        "isAdmin" to (it.role == Role.ADMIN),
        "phone" to hasPhonePreview,
        "hasComponents" to application.attributes.contains(ComponentStoreKey),
        "debugMode" to isDebugMode,
        "section" to section(request.path()),
    )
}

/** Whether admin debug mode (AI section reference IDs on headings) is enabled in settings. */
internal val ApplicationCall.isDebugMode: Boolean
    get() = application.attributes.getOrNull(SettingsStoreKey)?.get(AppKeys.DEBUG_MODE) == "true"

private val SettingsStoreKey = AttributeKey<SettingsStore>("composecast.settingsStore")

/** The side menu item a page belongs to (`_layout.ftl` marks it as the current page). */
internal fun section(path: String): String = when {
    path.startsWith("/admin/components") -> "components"
    path.startsWith("/admin/templates") -> "templates"
    path.startsWith("/admin/users") -> "users"
    path.startsWith("/admin/settings") -> "settings"
    else -> "screens" // screens, the visual editor, revisions
}

/** Whose draft previews these are (T-032, ADR 0030): one preview per user and screen. */
internal val AdminUser.previewOwner: String get() = "user:$id"

/** The user of the current request inside [loggedIn]. */
internal val ApplicationCall.adminUser: AdminUser get() = attributes[AdminUserKey]

private val AdminUserKey = AttributeKey<AdminUser>("composecast.adminUser")
private val SAFE_METHODS = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

/**
 * Routes that need a session: without one GET redirects to the login page and anything else is 401;
 * a role that cannot edit gets 403 on every mutating method, whatever route is added here later.
 */
private fun Route.loggedIn(auth: AdminAuth, build: Route.() -> Unit) {
    val child = createChild(LoggedInSelector)
    child.install(RequireUser) { this.auth = auth }
    child.build()
}

private object LoggedInSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString() = "(admin: logged in)"
}

private class RequireUserConfig {
    lateinit var auth: AdminAuth
}

private val RequireUser = createRouteScopedPlugin("AdminRequireUser", ::RequireUserConfig) {
    val auth = pluginConfig.auth
    onCall { call ->
        val user = call.currentUser(auth)
        when {
            user == null && call.request.httpMethod == HttpMethod.Get -> call.seeOther("/admin/login")
            user == null -> call.respond(HttpStatusCode.Unauthorized)
            call.request.httpMethod !in SAFE_METHODS && !user.role.canEdit -> call.respond(HttpStatusCode.Forbidden)
            else -> call.attributes.put(AdminUserKey, user)
        }
    }
}

/** Admin pages are private and never framed (clickjacking) or cached. */
private val SecurityHeaders = createRouteScopedPlugin("AdminSecurityHeaders") {
    onCall { call ->
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header("X-Frame-Options", "DENY")
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("Referrer-Policy", "same-origin")
    }
    onCallRespond { call, _ ->
        // Keep stricter route-specific CSP (e.g. SVG placeholder's default-src 'none').
        if (call.response.headers["Content-Security-Policy"] == null) {
            call.response.header(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline' https://unpkg.com; " +
                    "style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
                    "frame-ancestors 'none'; base-uri 'self'; form-action 'self'",
            )
        }
    }
}

private fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.origin.remoteAddress

private fun ApplicationCall.currentUser(auth: AdminAuth): AdminUser? =
    sessions.get<AdminSession>()?.let { auth.user(it.token) }

/**
 * Browsers drop a `Secure` cookie on plain HTTP (except on localhost), so logging in would silently
 * fail: say why instead. `X-Forwarded-Proto` comes from the HTTPS proxy (Caddy, profile `https`);
 * a forged one only makes the browser drop the cookie.
 */
private fun ApplicationCall.insecureTransportError(allowHttp: Boolean): String? {
    val https = request.origin.scheme == "https" ||
        request.headers["X-Forwarded-Proto"]?.substringBefore(',')?.trim() == "https"
    val loopback = request.host() in setOf("localhost", "127.0.0.1", "::1", "[::1]")
    if (allowHttp || https || loopback) return null
    return "The admin panel needs HTTPS (the login cookie is Secure). Enable the https profile, " +
        "or set COMPOSECAST_ADMIN_ALLOW_HTTP=true for local testing."
}

private suspend fun ApplicationCall.loginPage(
    error: String?,
    email: String = "",
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    response.status(status)
    respondTemplate("admin/login.ftl", mapOf("error" to error.orEmpty(), "email" to email))
}

/** 303: the browser follows a POST's redirect with a GET. */
internal suspend fun ApplicationCall.seeOther(path: String) {
    response.header(HttpHeaders.Location, path)
    respond(HttpStatusCode.SeeOther)
}
