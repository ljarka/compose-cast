package io.composecast.server.admin

import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ComponentDefinition
import io.composecast.protocol.ComponentDesign
import io.composecast.protocol.ComponentJson
import io.composecast.protocol.ComponentParam
import io.composecast.protocol.DesignAnchor
import io.composecast.protocol.DesignElement
import io.composecast.protocol.DesignElementType
import io.composecast.server.sdk.CustomComponents
import io.composecast.server.store.ComponentStore
import io.composecast.server.store.SettingsStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
            // New components start as a drawing (T-057; the designer shows the JSON too). Only
            // `start=json` (API, tests) creates a hand-written RC JSON starter.
            if (form["start"] != "json") {
                components.saveDraft(id, ComponentJson.encode(starterDesign(id)))
                return@post call.seeOther("/admin/components/${id.urlEncoded()}/design")
            }
            components.saveDraft(id, ComponentJson.encode(starter(id)))
            call.seeOther("/admin/components/${id.urlEncoded()}")
        }
        // T-057: the designer's script (a vanilla JS island, ADR 0034); ids cannot contain '.'.
        get("/designer.js") {
            val js = checkNotNull(ComponentStore::class.java.getResource("/admin-assets/designer.js")) {
                "missing resource admin-assets/designer.js"
            }.readBytes()
            call.respondBytes(js, ContentType.Application.JavaScript)
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
            get("/design") {
                val stored = components.get(call.screenId) ?: return@get call.notFound()
                val notice = NOTICES.entries.firstOrNull { it.key in call.request.queryParameters }?.value.orEmpty()
                val json = stored.draftJson ?: stored.liveJson.orEmpty()
                val brand = settings[SettingsStore.BRAND]?.let { Json.decodeFromString(BrandConfig.serializer(), it) }
                call.respondTemplate(
                    "admin/designer.ftl",
                    call.page(stored.id) + mapOf(
                        "id" to stored.id,
                        "json" to json.scriptSafe(),
                        "brand" to brandColors(brand).scriptSafe(),
                        "status" to screenStatus(stored),
                        "notice" to notice,
                        "hasDraft" to (stored.draftJson != null && stored.liveJson != null),
                        *timeFields("draftUpdatedAt", stored.updatedAt).toTypedArray(),
                        "history" to historyRows(components, stored.id),
                        "phoneImage" to (phoneRenderer != null),
                    ),
                )
            }
            // The designer saves the whole definition as JSON on every change (debounced) and gets
            // the problems back; a valid one becomes the draft.
            post("/design") {
                val stored = components.get(call.screenId) ?: return@post call.notFound()
                val checked = check(stored.id, call.receiveText(), settings)
                if (checked.problems.isEmpty()) {
                    components.saveDraft(stored.id, ComponentJson.encode(checked.definition!!))
                }
                val answer = buildJsonObject {
                    put("saved", checked.problems.isEmpty())
                    // The compiled RC JSON for the designer's "Remote Compose" tab.
                    checked.definition?.let { put("body", it.body) }
                    putJsonArray("problems") { checked.problems.forEach { add(JsonPrimitive(it)) } }
                }
                call.respondText(
                    answer.toString(),
                    ContentType.Application.Json,
                    if (checked.problems.isEmpty()) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity,
                )
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
                val params = call.receiveParameters()
                val json = params["json"] ?: stored.draftJson ?: stored.liveJson.orEmpty()
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
                val target = if (params["from"] == "design") "/design" else ""
                call.seeOther("/admin/components/${stored.id.urlEncoded()}$target?published")
            }
            post("/rollback") {
                val stored = components.get(call.screenId) ?: return@post call.notFound()
                val params = call.receiveParameters()
                val revision = params["revision"]?.toIntOrNull()
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
                val target = if (params["from"] == "design") "/design" else ""
                call.seeOther("/admin/components/${stored.id.urlEncoded()}$target?rolledback")
            }
            post("/draft/revert") {
                val stored = components.get(call.screenId) ?: return@post call.notFound()
                val params = call.receiveParameters()
                if (stored.liveJson == null || stored.draftJson == null) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    val json = stored.draftJson ?: stored.liveJson.orEmpty()
                    return@post call.editor(
                        components,
                        stored,
                        json,
                        ComponentJson.Checked(null, listOf("There is no pending draft to revert.")),
                        null,
                        phoneRenderer != null,
                    )
                }
                components.discardDraft(stored.id)
                val target = if (params["from"] == "design") "/design" else ""
                call.seeOther("/admin/components/${stored.id.urlEncoded()}$target?draftReverted")
            }
            get("/revisions/{revision}") {
                val revision =
                    call.parameters["revision"]?.toIntOrNull()?.let { components.revision(call.screenId, it) }
                        ?: return@get call.notFound()
                call.respondTemplate(
                    "admin/revision.ftl",
                    call.page("${revision.screenId} #${revision.id}") + mapOf(
                        "id" to revision.screenId,
                        "revision" to revision.id,
                        "json" to revision.json,
                        "backUrl" to "/admin/components/${revision.screenId.urlEncoded()}",
                        *timeFields("publishedAt", revision.publishedAt).toTypedArray(),
                        "publishedBy" to revision.publishedBy,
                    ),
                )
            }
            post("/revisions/{revision}/delete") {
                val stored = components.get(call.screenId) ?: return@post call.notFound()
                val params = call.receiveParameters()
                val revId = call.parameters["revision"]?.toIntOrNull()
                    ?.takeIf { components.revision(stored.id, it) != null } ?: return@post call.notFound()
                if (components.history(stored.id).firstOrNull()?.id == revId) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    val json = stored.draftJson ?: stored.liveJson.orEmpty()
                    return@post call.editor(
                        components,
                        stored,
                        json,
                        ComponentJson.Checked(null, listOf("Cannot remove the currently live revision #$revId.")),
                        null,
                        phoneRenderer != null,
                    )
                }
                components.deleteRevision(stored.id, revId)
                val target = if (params["from"] == "design") "/design" else ""
                call.seeOther("/admin/components/${stored.id.urlEncoded()}$target?revisionRemoved")
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
                // The Components list: the component alone, cut from the phone image and scaled down.
                get("/thumb.png") {
                    val stored = components.get(call.screenId)
                        ?: return@get call.respondBytes(ByteArray(0), ContentType.Text.Plain, HttpStatusCode.NotFound)
                    val json = stored.draftJson ?: stored.liveJson.orEmpty()
                    val png = try {
                        withContext(Dispatchers.IO) {
                            componentThumbnail(phoneRenderer.png(publishing.componentDocument(json)))
                        }
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
    "draftReverted" to "Draft reverted: showing the last published version.",
    "revisionRemoved" to "Removed the revision from history.",
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

/** A first design: a rounded card in the brand color with a title param (T-057). */
private fun starterDesign(id: String) = ComponentDefinition(
    id = id,
    rcVersion = CustomComponents.RC_VERSION,
    params = mapOf("title" to ComponentParam(default = "Hello", description = "the card's title")),
    design = ComponentDesign(
        height = 120f,
        cornerRadius = 16f,
        elements = listOf(
            DesignElement(
                id = "bg",
                name = "Background",
                type = DesignElementType.Rect,
                w = ComponentDesign.DEFAULT_WIDTH,
                h = 120f,
                anchor = DesignAnchor.Stretch,
                fill = "{{brand.primaryColor}}",
            ),
            DesignElement(
                id = "title",
                name = "Title",
                type = DesignElementType.Text,
                x = 20f,
                y = 20f,
                w = 280f,
                h = 30f,
                fill = "#FFFFFFFF",
                text = "{{params.title}}",
                fontSize = 22f,
                bold = true,
            ),
        ),
    ),
)

/** `{{brand.<field>}}` values for the designer's preview: the set brand, else the renderer's look. */
private fun brandColors(brand: BrandConfig?): String = buildJsonObject {
    put("primaryColor", brand?.primaryColor ?: "#FF3B5BDB")
    put("secondaryColor", brand?.secondaryColor ?: brand?.primaryColor ?: "#FF3B5BDB")
    put("backgroundColor", brand?.backgroundColor ?: "#FFFFFFFF")
    put("surfaceColor", brand?.surfaceColor ?: "#FFF4F4F6")
    put("textColor", brand?.textColor ?: "#FF1D1D1F")
    put("outlineColor", brand?.outlineColor ?: "#FFDCDCE0")
}.toString()

/** JSON for a `<script type="application/json">`: `</` cannot end the script element early. */
private fun String.scriptSafe() = replace("</", "<\\/")

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
            *timeFields("publishedAt", stored.publishedAt).toTypedArray(),
            "publishedBy" to stored.publishedBy.orEmpty(),
            "designed" to (definition?.design != null),
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
            *timeFields("publishedAt", stored.publishedAt).toTypedArray(),
            "publishedBy" to stored.publishedBy.orEmpty(),
            "notice" to notice.orEmpty(),
            "hasDraft" to (stored.draftJson != null && stored.liveJson != null),
            *timeFields("draftUpdatedAt", stored.updatedAt).toTypedArray(),
            "history" to historyRows(components, stored.id),
            "usage" to (definition?.let(::usage) ?: ""),
            "designed" to (definition?.design != null || "\"design\"" in json),
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
