package io.composecast.server.admin

import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.ScreenJson
import io.composecast.server.store.ScreenStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.net.URLEncoder

/**
 * Screens in the admin panel (T-007 A3): the list, the JSON editor with validation while typing
 * (HTMX → [validate]) and drafts. Only valid screens are saved, as canonical JSON (ADR 0016); a
 * draft never changes what apps are served (`ScreenStore.live`).
 */
internal fun Route.screenPages(screens: ScreenStore) {
    route("/screens") {
        get {
            call.respondTemplate("admin/screens.ftl", call.page("Screens") + mapOf("screens" to rows(screens)))
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
                        mapOf("screens" to rows(screens), "error" to error, "newId" to id, "newTitle" to title),
                )
            }
            screens.saveDraft(id, ScreenJson.encode(ScreenDefinition(id = id, title = title)))
            call.seeOther("/admin/screens/${id.urlEncoded()}")
        }
        route("/{id}") {
            get {
                val stored = screens.get(call.screenId) ?: return@get call.notFound()
                val json = stored.draftJson ?: stored.liveJson.orEmpty()
                call.editor(
                    stored,
                    json,
                    problems(call.screenId, json),
                    saved = "saved" in call.request.queryParameters,
                )
            }
            post {
                val stored = screens.get(call.screenId) ?: return@post call.notFound()
                val json = call.receiveParameters()["json"].orEmpty()
                val checked = problems(stored.id, json)
                if (checked.problems.isNotEmpty()) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.editor(stored, json, checked, saved = false)
                }
                screens.saveDraft(stored.id, ScreenJson.encode(checked.screen!!))
                call.seeOther("/admin/screens/${stored.id.urlEncoded()}?saved")
            }
            post("/validate") {
                val json = call.receiveParameters()["json"].orEmpty()
                call.respondTemplate("admin/_problems.ftl", problemsModel(problems(call.screenId, json)))
            }
        }
    }
}

private fun rows(screens: ScreenStore) = screens.list().map { stored ->
    val json = stored.draftJson ?: stored.liveJson
    mapOf(
        "id" to stored.id,
        "title" to (json?.let { ScreenJson.check(it).screen?.title } ?: stored.id),
        "status" to status(stored),
        "publishedAt" to stored.publishedAt?.toString().orEmpty(),
        "publishedBy" to stored.publishedBy.orEmpty(),
    )
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
    stored: ScreenStore.StoredScreen,
    json: String,
    checked: ScreenJson.Checked,
    saved: Boolean,
) = respondTemplate(
    "admin/screen.ftl",
    page(stored.id) + problemsModel(checked) + mapOf(
        "id" to stored.id,
        "json" to json,
        "status" to status(stored),
        "publishedAt" to stored.publishedAt?.toString().orEmpty(),
        "publishedBy" to stored.publishedBy.orEmpty(),
        "saved" to saved,
    ),
)

private val ApplicationCall.screenId: String get() = parameters["id"].orEmpty()

private suspend fun ApplicationCall.notFound() = respondText("No screen '$screenId'", status = HttpStatusCode.NotFound)

private fun String.urlEncoded() = URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
