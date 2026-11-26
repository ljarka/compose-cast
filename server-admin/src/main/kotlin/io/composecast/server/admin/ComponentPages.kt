package io.composecast.server.admin

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentJson
import io.composecast.protocol.ComponentParam
import io.composecast.server.sdk.CustomComponents
import io.composecast.server.store.ComponentStore
import io.composecast.server.store.SettingsStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Custom components in the admin panel (T-049, ADR 0033): list, JSON editor with the full check
 * (definition, whitelist, trial parse with the live brand) while typing, drafts, publish, history,
 * rollback, remove, and a phone image of the component alone with its sample arguments.
 */
internal fun Route.componentPages(
    components: ComponentStore,
    settings: SettingsStore,
    publishing: ScreenPublishing,
    phoneRenderer: PhoneRenderer?,
) {
    route("/components") {
        get { call.list(components) }
        post {
            val form = call.receiveParameters()
            val id = form["id"].orEmpty().trim()
            val error = when {
                !COMPONENT_ID.matches(
                    id,
                ) -> "The id may use a–z, 0–9, '-' and '_' (at most 64, starting with a letter or digit)."
                components.get(id) != null -> "A component '$id' already exists."
                else -> null
            }
            if (error != null) {
                call.response.status(HttpStatusCode.UnprocessableEntity)
                return@post call.list(components, error, id)
            }
            components.saveDraft(id, ComponentJson.encode(starter(id)))
            call.seeOther("/admin/components/${id.urlEncoded()}")
        }
        route("/{id}") {
            get {
                val stored = components.get(call.screenId) ?: return@get call.notFound()
                val notice = NOTICES.entries.firstOrNull { it.key in call.request.queryParameters }?.value
                val json = stored.draftJson ?: stored.liveJson.orEmpty()
                call.editor(components, stored, json, check(stored.id, json, settings), notice, phoneRenderer != null)
            }
            post {
                val stored = components.get(call.screenId) ?: return@post call.notFound()
                val json = call.receiveParameters()["json"].orEmpty()
                val checked = check(stored.id, json, settings)
                if (checked.problems.isNotEmpty()) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.editor(components, stored, json, checked, null, phoneRenderer != null)
                }
                components.saveDraft(stored.id, ComponentJson.encode(checked.definition!!))
                call.seeOther("/admin/components/${stored.id.urlEncoded()}?saved")
            }
            post("/validate") {
                val json = call.receiveParameters()["json"].orEmpty()
                val checked = check(call.screenId, json, settings)
                call.respondTemplate(
                    "admin/_problems.ftl",
                    mapOf(
                        "problems" to checked.problems,
                        "warnings" to emptyList<String>(),
                    ),
                )
            }
            post("/publish") {
                val stored = components.get(call.screenId) ?: return@post call.notFound()
                val json = call.receiveParameters()["json"] ?: stored.draftJson ?: stored.liveJson.orEmpty()
                val checked = check(stored.id, json, settings)
                val failure = checked.definition?.let { definition ->
                    publishOrProblem {
                        publishing.publishComponent(stored.id, ComponentJson.encode(definition), call.adminUser.email)
                    }
                }
                val problems = failure?.let { listOf(it) } ?: checked.problems
                if (problems.isNotEmpty()) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.editor(
                        components,
                        stored,
                        json,
                        ComponentJson.Checked(null, problems),
                        null,
                        phoneRenderer != null,
                    )
                }
                call.seeOther("/admin/components/${stored.id.urlEncoded()}?published")
            }
            post("/rollback") {
                val stored = components.get(call.screenId) ?: return@post call.notFound()
                val revision = call.receiveParameters()["revision"]?.toIntOrNull()
                    ?.let { components.revision(stored.id, it) } ?: return@post call.notFound()
                val failure = publishOrProblem {
                    publishing.rollbackComponent(stored.id, revision.id, call.adminUser.email)
                }
                if (failure != null) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    val json = stored.draftJson ?: stored.liveJson.orEmpty()
                    return@post call.editor(
                        components,
                        stored,
                        json,
                        ComponentJson.Checked(null, listOf(failure)),
                        null,
                        phoneRenderer != null,
                    )
                }
                call.seeOther("/admin/components/${stored.id.urlEncoded()}?rolledback")
            }
            post("/delete") {
                val stored = components.get(call.screenId) ?: return@post call.notFound()
                val failure = if (stored.liveJson == null) {
                    components.delete(stored.id)
                    null
                } else {
                    publishOrProblem { publishing.deleteComponent(stored.id) }
                        ?.replace("Not published", "Not removed")
                }
                if (failure != null) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.list(components, error = failure)
                }
                call.seeOther("/admin/components")
            }
            if (phoneRenderer != null) {
                get("/phone.png") {
                    val stored = components.get(call.screenId)
                        ?: return@get call.respondBytes(ByteArray(0), ContentType.Text.Plain, HttpStatusCode.NotFound)
                    val json = stored.draftJson ?: stored.liveJson.orEmpty()
                    val png = try {
                        phoneRenderer.png(publishing.componentDocument(json))
                    } catch (e: Exception) {
                        return@get call.respondBytes(
                            placeholderSvg("No preview of this component", e.message.orEmpty()).toByteArray(),
                            ContentType.Image.SVG,
                        )
                    }
                    call.respondBytes(png, ContentType.Image.PNG)
                }
            }
        }
    }
}

/** The live custom components for the visual editor's palette (T-049); absent = feature off. */
internal val ComponentStoreKey = AttributeKey<ComponentStore>("composecast.components")

/** Live custom component definitions by id (empty when components are off). */
internal fun ApplicationCall.customComponents(): Map<String, ComponentDefinition> =
    application.attributes.getOrNull(ComponentStoreKey)?.live().orEmpty()
        .mapNotNull { (id, json) -> ComponentJson.check(json).definition?.let { id to it } }.toMap()

/** New component ids: safe in URLs and file names (`components/<id>.json`). */
private val COMPONENT_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")

private val NOTICES = mapOf(
    "saved" to "Draft saved (as formatted JSON). Screens still use the published version.",
    "published" to "Published: screens use this version from their next render.",
    "rolledback" to "Rolled back: screens use the chosen version. A pending draft was kept.",
)

/** A valid first draft: a rounded box with a text param, to be replaced (by hand or by an LLM). */
private fun starter(id: String) = ComponentDefinition(
    id = id,
    rcVersion = CustomComponents.RC_VERSION,
    params = mapOf("title" to ComponentParam(default = "Hello", description = "the text in the box")),
    body = Json.parseToJsonElement(
        """{ "type": "box", "horizontalAlignment": "center", "verticalAlignment": "center",
             "modifiers": [ { "fillMaxWidth": 1 }, { "height": 96 }, { "clip": 16 },
                            { "background": "{{brand.primaryColor}}" } ],
             "children": [ { "type": "text", "text": "{{params.title}}", "color": "#FFFFFF", "fontSize": 20 } ] }""",
    ).jsonObject,
)

/**
 * [ComponentJson.check] + the id rule + [CustomComponents.check] (whitelist, trial parse) with the
 * live brand: what publishing would find, while typing.
 */
private fun check(id: String, json: String, settings: SettingsStore): ComponentJson.Checked {
    val checked = ComponentJson.check(json)
    val definition = checked.definition ?: return checked
    if (definition.id != id) {
        return ComponentJson.Checked(null, listOf("id: must be '$id' (to use another id, create a new component)"))
    }
    val brand = settings[SettingsStore.BRAND]?.let { Json.decodeFromString(BrandConfig.serializer(), it) }
    val problems = CustomComponents.check(definition, brand)
    return ComponentJson.Checked(definition.takeIf { problems.isEmpty() }, problems)
}

private suspend fun ApplicationCall.list(components: ComponentStore, error: String? = null, newId: String = "") {
    val rows = components.list().map { stored ->
        val definition = (stored.draftJson ?: stored.liveJson)?.let { ComponentJson.check(it).definition }
        mapOf(
            "id" to stored.id,
            "title" to (definition?.title ?: stored.id),
            "description" to definition?.description.orEmpty(),
            "version" to (definition?.version ?: 0),
            "rcVersion" to definition?.rcVersion.orEmpty(),
            "status" to screenStatus(stored),
            "publishedAt" to stored.publishedAt?.toString().orEmpty(),
            "publishedBy" to stored.publishedBy.orEmpty(),
        )
    }
    respondTemplate(
        "admin/components.ftl",
        page("Components") + mapOf(
            "components" to rows,
            "error" to error.orEmpty(),
            "newId" to newId,
            "rcVersion" to CustomComponents.RC_VERSION,
        ),
    )
}

private suspend fun ApplicationCall.editor(
    components: ComponentStore,
    stored: io.composecast.server.store.ScreenStore.StoredScreen,
    json: String,
    checked: ComponentJson.Checked,
    notice: String?,
    phone: Boolean,
) {
    val definition = checked.definition ?: ComponentJson.check(json).definition
    respondTemplate(
        "admin/component.ftl",
        page(stored.id) + mapOf(
            "id" to stored.id,
            "json" to json,
            "problems" to checked.problems,
            "warnings" to emptyList<String>(),
            "status" to screenStatus(stored),
            "publishedAt" to stored.publishedAt?.toString().orEmpty(),
            "publishedBy" to stored.publishedBy.orEmpty(),
            "notice" to notice.orEmpty(),
            "history" to components.history(stored.id).map {
                mapOf("revision" to it.id, "publishedAt" to it.publishedAt.toString(), "publishedBy" to it.publishedBy)
            },
            "usage" to (definition?.let(::usage) ?: ""),
            "phoneImage" to phone,
            "rcVersion" to CustomComponents.RC_VERSION,
        ),
    )
}

/** The screen JSON that uses [definition], with its required params filled with samples. */
private fun usage(definition: ComponentDefinition): String {
    val use = Palette.customStarter(definition.id, CustomComponents.requiredSampleArgs(definition))
    return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), use)
}

private suspend fun ApplicationCall.notFound() =
    respondText("No component '$screenId'", status = HttpStatusCode.NotFound)

private fun String.urlEncoded() = URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
