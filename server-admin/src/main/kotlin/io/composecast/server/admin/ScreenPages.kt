package io.composecast.server.admin

import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import io.composecast.server.store.ScreenStore
import io.composecast.server.store.SettingsStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.net.URLEncoder

/**
 * Screens in the admin panel (T-007 A3, A4): the list, the JSON editor with validation while typing
 * (HTMX → `validate`), drafts, publish, history and rollback. Only valid screens are saved, as
 * canonical JSON (ADR 0016); a draft never changes what apps are served (`ScreenStore.live`);
 * publishing goes through [ScreenPublishing], which changes nothing when the screens would not build.
 */
internal fun Route.screenPages(screens: ScreenStore, settings: SettingsStore, publishing: ScreenPublishing) {
    route("/screens") {
        get {
            call.respondTemplate(
                "admin/screens.ftl",
                call.page("Screens") + mapOf("screens" to rows(screens, settings)),
            )
        }
        post {
            val form = call.receiveParameters()
            val id = form["id"].orEmpty().trim()
            val title = form["title"].orEmpty().trim().ifEmpty { id }
            val error = when {
                !ID.matches(id) -> "The id may use a–z, 0–9, '-' and '_' (at most 64, starting with a letter or digit)."
                screens.get(id) != null -> "A screen '$id' already exists."
                else -> null
            }
            if (error != null) {
                call.response.status(HttpStatusCode.UnprocessableEntity)
                return@post call.respondTemplate(
                    "admin/screens.ftl",
                    call.page("Screens") +
                        mapOf(
                            "screens" to rows(screens, settings),
                            "error" to error,
                            "newId" to id,
                            "newTitle" to title,
                        ),
                )
            }
            screens.saveDraft(id, ScreenJson.encode(ScreenDefinition(id = id, title = title)))
            call.seeOther("/admin/screens/${id.urlEncoded()}")
        }
        route("/{id}") {
            get {
                val stored = screens.get(call.screenId) ?: return@get call.notFound()
                val json = stored.draftJson ?: stored.liveJson.orEmpty()
                val notice = NOTICES.entries.firstOrNull { it.key in call.request.queryParameters }?.value
                call.editor(screens, stored, json, problems(call.screenId, json), notice)
            }
            post {
                val stored = screens.get(call.screenId) ?: return@post call.notFound()
                val json = call.receiveParameters()["json"].orEmpty()
                val checked = problems(stored.id, json)
                if (checked.problems.isNotEmpty()) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.editor(screens, stored, json, checked)
                }
                screens.saveDraft(stored.id, ScreenJson.encode(checked.screen!!))
                call.seeOther("/admin/screens/${stored.id.urlEncoded()}?saved")
            }
            post("/validate") {
                val json = call.receiveParameters()["json"].orEmpty()
                call.respondTemplate("admin/_problems.ftl", problemsModel(problems(call.screenId, json)))
            }
            // The editor's "Publish" submits the textarea: exactly what is shown goes live.
            post("/publish") {
                val stored = screens.get(call.screenId) ?: return@post call.notFound()
                val json = call.receiveParameters()["json"].orEmpty()
                val checked = problems(stored.id, json)
                val shown = checked.screen?.let { screen ->
                    val failure = publishOrProblem {
                        publishing.publish(stored.id, ScreenJson.encode(screen), call.adminUser.email)
                    }
                    failure?.let { ScreenJson.Checked(null, listOf(it)) }
                } ?: checked.takeIf { it.problems.isNotEmpty() }
                if (shown != null) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.editor(screens, stored, json, shown)
                }
                call.seeOther("/admin/screens/${stored.id.urlEncoded()}?published")
            }
            // T-045 B2: the textarea as a temporary screen for the Preview app; nothing is saved.
            post("/preview") {
                val stored = screens.get(call.screenId) ?: return@post call.notFound()
                val json = call.receiveParameters()["json"].orEmpty()
                val checked = problems(stored.id, json)
                val screen = checked.screen ?: run {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.editor(screens, stored, json, checked)
                }
                val previewId = try {
                    publishing.preview(ScreenJson.encode(screen))
                } catch (e: PublishException) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.editor(
                        screens,
                        stored,
                        json,
                        ScreenJson.Checked(null, listOf("No preview: ${e.message}")),
                    )
                }
                val serverUrl = call.publicServerUrl() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val connect = "$serverUrl/connect?screen=$previewId"
                call.editor(
                    screens,
                    stored,
                    json,
                    checked,
                    preview = mapOf("url" to connect, "qr" to QrCode.svg(connect), "screen" to previewId),
                )
            }
            post("/home") {
                val stored = screens.get(call.screenId) ?: return@post call.notFound()
                val failure = publishOrProblem { publishing.setHome(stored.id) }
                if (failure != null) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.respondTemplate(
                        "admin/screens.ftl",
                        call.page("Screens") + mapOf("screens" to rows(screens, settings), "homeError" to failure),
                    )
                }
                call.seeOther("/admin/screens")
            }
            post("/rollback") {
                val stored = screens.get(call.screenId) ?: return@post call.notFound()
                val revision = call.receiveParameters()["revision"]?.toIntOrNull()
                    ?.let { screens.revision(stored.id, it) } ?: return@post call.notFound()
                val failure = publishOrProblem { publishing.rollback(stored.id, revision.id, call.adminUser.email) }
                if (failure != null) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    val json = stored.draftJson ?: stored.liveJson.orEmpty()
                    return@post call.editor(screens, stored, json, ScreenJson.Checked(null, listOf(failure)))
                }
                call.seeOther("/admin/screens/${stored.id.urlEncoded()}?rolledback")
            }
            get("/revisions/{revision}") {
                val revision = call.parameters["revision"]?.toIntOrNull()?.let { screens.revision(call.screenId, it) }
                    ?: return@get call.notFound()
                call.respondTemplate(
                    "admin/revision.ftl",
                    call.page("${revision.screenId} #${revision.id}") + mapOf(
                        "id" to revision.screenId,
                        "revision" to revision.id,
                        "json" to revision.json,
                        "publishedAt" to revision.publishedAt.toString(),
                        "publishedBy" to revision.publishedBy,
                    ),
                )
            }
        }
    }
}

private val NOTICES = mapOf(
    "saved" to "Draft saved (as formatted JSON). Apps still get the live version.",
    "published" to "Published: apps get this version from their next request.",
    "rolledback" to "Rolled back: apps get the chosen version from their next request. A pending draft was kept.",
)

/** Runs a publish; its failure becomes a message for the editor (null = success). */
private inline fun publishOrProblem(publish: () -> Unit): String? = try {
    publish()
    null
} catch (e: PublishException) {
    "Not published, nothing changed: ${e.message}"
}

private fun rows(screens: ScreenStore, settings: SettingsStore): List<Map<String, Any>> {
    val home = settings[SettingsStore.HOME_SCREEN]?.takeIf { it.isNotBlank() } ?: "main"
    return screens.list().map { stored ->
        val json = stored.draftJson ?: stored.liveJson
        mapOf(
            "id" to stored.id,
            "title" to (json?.let { ScreenJson.check(it).screen?.title } ?: stored.id),
            "status" to status(stored),
            "isHome" to (stored.id == home),
            "isLive" to (stored.liveJson != null),
            "publishedAt" to stored.publishedAt?.toString().orEmpty(),
            "publishedBy" to stored.publishedBy.orEmpty(),
        )
    }
}

/** New screen ids: safe in URLs and file names (`<id>.json`, ADR 0011). */
private val ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")

/** `live` (published, no pending edit), `draft` (published + pending edit), `unpublished` (only a draft). */
private fun status(screen: ScreenStore.StoredScreen) = when {
    screen.liveJson == null -> "unpublished"
    screen.draftJson != null -> "draft"
    else -> "live"
}

/** [ScreenJson.check] plus the rule that a screen keeps its id (the URL and the stored row). */
private fun problems(id: String, json: String): ScreenJson.Checked {
    val checked = ScreenJson.check(json)
    val screen = checked.screen ?: return checked
    if (screen.id == id) return checked
    return ScreenJson.Checked(null, listOf("id: must be '$id' (to use another id, create a new screen)"))
}

private fun problemsModel(checked: ScreenJson.Checked) = mapOf(
    "problems" to checked.problems,
    "warnings" to (checked.screen?.warnings() ?: emptyList()),
)

private suspend fun ApplicationCall.editor(
    screens: ScreenStore,
    stored: ScreenStore.StoredScreen,
    json: String,
    checked: ScreenJson.Checked,
    notice: String? = null,
    preview: Map<String, String>? = null,
) = respondTemplate(
    "admin/screen.ftl",
    page(stored.id) + problemsModel(checked) + mapOf(
        "id" to stored.id,
        "json" to json,
        "status" to status(stored),
        "publishedAt" to stored.publishedAt?.toString().orEmpty(),
        "publishedBy" to stored.publishedBy.orEmpty(),
        "notice" to notice.orEmpty(),
        "preview" to (preview ?: emptyMap()),
        "history" to screens.history(stored.id).map {
            mapOf("revision" to it.id, "publishedAt" to it.publishedAt.toString(), "publishedBy" to it.publishedBy)
        },
    ),
)

/** The `{id}` path segment (not `parameters`, which also holds the query). */
internal val ApplicationCall.screenId: String get() = (this as RoutingCall).pathParameters["id"].orEmpty()

/** Host header characters of a hostname, IPv4/IPv6 address and port. */
private val HOST = Regex("[A-Za-z0-9.\\-:\\[\\]]{1,255}")

/**
 * This server as the phone reaches it (same rule as `server-app`'s `ConnectPage.serverUrl`):
 * `X-Forwarded-Proto` from the HTTPS proxy or the connection's scheme, plus the `Host` header.
 */
private fun ApplicationCall.publicServerUrl(): String? {
    val host =
        (request.headers["Host"] ?: "${request.host()}:${request.port()}").takeIf { HOST.matches(it) } ?: return null
    val forwarded = request.headers["X-Forwarded-Proto"]?.substringBefore(',')?.trim()?.lowercase()
    return "${forwarded?.takeIf { it == "https" || it == "http" } ?: request.origin.scheme}://$host"
}

private suspend fun ApplicationCall.notFound() = respondText("No screen '$screenId'", status = HttpStatusCode.NotFound)

private fun String.urlEncoded() = URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
