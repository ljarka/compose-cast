package io.composecast.server.admin

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenJson
import io.composecast.server.store.ScreenStore
import io.composecast.server.store.SettingsStore
import io.composecast.templates.AppTemplate
import io.composecast.templates.AppTemplates
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json

/**
 * App templates (T-030, ADR 0031): pick a whole app (News, Shop, Events, Weather, Blank) → its screens
 * become **drafts** (nothing goes live), optionally its colors become the brand → "Publish all" makes
 * the screens live and the template's home screen the app's home. Id clashes: the user picks a prefix.
 */
internal fun Route.templatesPages(screens: ScreenStore, settings: SettingsStore, publishing: ScreenPublishing) {
    route("/templates") {
        get { call.templatesPage(screens) }
        post("/{template}") {
            val template = AppTemplates[call.parameters["template"].orEmpty()] ?: return@post call.noTemplate()
            val form = call.receiveParameters()
            val prefix = form["prefix"].orEmpty().trim()
            if (prefix.isNotEmpty() && !PREFIX.matches(prefix)) {
                return@post call.templatesPage(
                    screens,
                    template.id to "A prefix uses a-z, 0-9, - and _ (e.g. ${template.id}-).",
                )
            }
            val app = template.withPrefix(prefix)
            val taken = app.screens.map { it.id }.filter { screens.get(it) != null }
            if (taken.isNotEmpty()) {
                return@post call.templatesPage(
                    screens,
                    template.id to
                        "Screens ${taken.joinToString()} already exist. Add a prefix, e.g. \"${template.id}-\".",
                )
            }
            app.screens.forEach { screens.saveDraft(it.id, ScreenJson.encode(it)) }
            val brandError = if (form["brand"] == "on") applyBrand(app, settings, publishing) else null
            call.templatesPage(
                screens,
                applied = Applied(
                    template,
                    app,
                    prefix,
                    brandApplied =
                    form["brand"] == "on" && brandError == null,
                    brandError,
                ),
            )
        }
        post("/{template}/publish") {
            val template = AppTemplates[call.parameters["template"].orEmpty()] ?: return@post call.noTemplate()
            val prefix = call.receiveParameters()["prefix"].orEmpty().trim()
            val app = template.withPrefix(prefix)
            val failure = publishOrProblem {
                app.screens.forEach { screen ->
                    val stored = screens.get(screen.id)
                        ?: throw PublishException("Screen '${screen.id}' does not exist: use the template first")
                    stored.draftJson?.let { publishing.publish(screen.id, it, call.adminUser.email) }
                }
                publishing.setHome(app.manifest.home)
            }
            if (failure != null) {
                call.response.status(HttpStatusCode.UnprocessableEntity)
                return@post call.templatesPage(screens, template.id to failure)
            }
            call.seeOther("/admin/screens")
        }
    }
}

private suspend fun ApplicationCall.noTemplate() = respondText("No such template", status = HttpStatusCode.NotFound)

private val PREFIX = Regex("[a-z0-9][a-z0-9_\\-]{0,30}")

/** The template's colors on top of the current brand (name, font and home stay), made live at once. */
private fun applyBrand(app: AppTemplate, settings: SettingsStore, publishing: ScreenPublishing): String? {
    val colors = app.brand ?: return null
    val current = settings[SettingsStore.BRAND]?.let { Json.decodeFromString(BrandConfig.serializer(), it) }
    val brand = current?.copy(
        primaryColor = colors.primaryColor,
        secondaryColor = colors.secondaryColor,
        backgroundColor = colors.backgroundColor,
        surfaceColor = colors.surfaceColor,
        textColor = colors.textColor,
        outlineColor = colors.outlineColor,
    ) ?: colors
    return publishOrProblem { publishing.publishBrand(Json.encodeToString(BrandConfig.serializer(), brand)) }
}

private class Applied(
    val template: AppTemplate,
    val app: AppTemplate,
    val prefix: String,
    val brandApplied: Boolean,
    val brandError: String?,
)

private suspend fun ApplicationCall.templatesPage(
    screens: ScreenStore,
    error: Pair<String, String>? = null,
    applied: Applied? = null,
) {
    if (error != null) response.status(HttpStatusCode.UnprocessableEntity)
    respondTemplate(
        "admin/templates.ftl",
        page("Templates") + mapOf(
            "empty" to screens.isEmpty(),
            "templates" to AppTemplates.all.map { t ->
                mapOf(
                    "id" to t.id,
                    "name" to t.manifest.name,
                    "description" to t.manifest.description,
                    "screens" to t.manifest.screens,
                    "secrets" to t.manifest.secrets.map { mapOf("name" to it.name, "why" to it.why) },
                    "live" to t.screens.any { it.live != null },
                    "error" to (error?.takeIf { it.first == t.id }?.second ?: ""),
                )
            },
            "applied" to (
                applied?.let {
                    mapOf(
                        "id" to it.template.id,
                        "name" to it.template.manifest.name,
                        "prefix" to it.prefix,
                        "home" to it.app.manifest.home,
                        "screens" to it.app.screens.map { s -> s.id },
                        "brandApplied" to it.brandApplied,
                        "brandError" to (it.brandError ?: ""),
                        "secrets" to it.template.manifest.secrets.map { s -> mapOf("name" to s.name, "why" to s.why) },
                    )
                } ?: emptyMap()
                ),
        ),
    )
}
