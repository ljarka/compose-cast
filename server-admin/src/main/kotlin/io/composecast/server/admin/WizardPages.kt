package io.composecast.server.admin

import io.composecast.protocol.ScreenJson
import io.composecast.server.store.ScreenStore
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.net.URI
import java.net.URLEncoder

/**
 * The data source wizard (T-031 W2): paste an API address → the server probes it ([ApiProbe]) →
 * pick the list and its fields (pre-filled by [ApiShape]) → a list screen and a detail screen are
 * created as drafts ([WizardScreens]) and opened in the visual editor. Never stores a raw key.
 */
internal fun Route.wizardPages(screens: ScreenStore, probe: ApiProbe) {
    get("/wizard") { call.wizardPage(WizardForm(Parameters.Empty), null, emptyList()) }
    post("/wizard") {
        val form = WizardForm(call.receiveParameters())
        if (form.url.isBlank()) return@post call.wizardPage(form, null, listOf("Enter the API's address."))
        val result = probe.probe(form.url, form.headers)
        val json = result.json
        if (json == null) {
            call.response.status(HttpStatusCode.UnprocessableEntity)
            return@post call.wizardPage(form, result, listOf("Could not use the response: ${result.error}"))
        }
        val lists = ApiShape.lists(json)
        if (lists.isEmpty()) {
            call.response.status(HttpStatusCode.UnprocessableEntity)
            return@post call.wizardPage(form, result, listOf("The response has no list of objects to show."))
        }
        val analysis = form.analyse(lists)
        if (form["step"] != "create") return@post call.wizardPage(form, result, emptyList(), analysis)

        val problems = analysis.problems(screens)
        if (problems.isNotEmpty()) {
            call.response.status(HttpStatusCode.UnprocessableEntity)
            return@post call.wizardPage(form, result, problems, analysis)
        }
        val plan = analysis.plan
        val generated = listOf(WizardScreens.list(plan), WizardScreens.detail(plan))
        val invalid = generated.flatMap { screen ->
            ScreenJson.check(ScreenJson.encode(screen)).problems.map { "${screen.id}: $it" }
        }
        if (invalid.isNotEmpty()) {
            call.response.status(HttpStatusCode.UnprocessableEntity)
            return@post call.wizardPage(form, result, invalid, analysis)
        }
        generated.forEach { screens.saveDraft(it.id, ScreenJson.encode(it)) }
        call.seeOther("/admin/screens/${plan.listScreen.urlEncoded()}/visual?node=&saved")
    }
}

/** The wizard's form fields; empty ones fall back to the guesses. */
private class WizardForm(private val params: Parameters) {
    operator fun get(name: String): String = params[name]?.trim().orEmpty()

    val url get() = this["url"]
    val headers: Map<String, String> get() = this["headerName"].takeIf {
        it.isNotEmpty()
    }?.let { mapOf(it to this["headerValue"]) }.orEmpty()

    fun analyse(lists: List<ApiShape.ItemList>): Analysis {
        // Another list picked: its own guesses, not the previous list's choices.
        val keepChoices = this["previousList"] == this["list"]
        val list = lists.firstOrNull { it.path == this["list"] } ?: lists.first()
        val guesses = ApiShape.roles(list.fields)
        fun choice(name: String, guess: String?) = if (keepChoices &&
            params.contains(name)
        ) {
            this[name].ifEmpty { null }
        } else {
            guess
        }
        val source = this["source"].ifEmpty { sourceName(url, list.path) }
        val id = choice("id", guesses.id)
        val listScreen = this["listScreen"].ifEmpty { source.lowercase().replace('_', '-') }
        val detailMode = this["detailMode"].ifEmpty { if (id != null) "url" else "params" }
        val plan = WizardScreens.Plan(
            source = source,
            url = url,
            headers = headers,
            listPath = list.path,
            id = id,
            title = choice("title", guesses.title).orEmpty(),
            subtitle = choice("subtitle", guesses.subtitle),
            image = choice("image", guesses.image),
            html = list.fields.filter { '<' in it.sample }.mapTo(HashSet()) { it.path },
            listScreen = listScreen,
            listTitle = this["listTitle"].ifEmpty { listScreen.replaceFirstChar(Char::uppercase) },
            detailScreen = this["detailScreen"].ifEmpty { "$listScreen-detail" },
            detailUrl = if (detailMode ==
                "url"
            ) {
                this["detailUrl"].ifEmpty { "${url.substringBefore('?').trimEnd('/')}/{{params.id}}" }
            } else {
                null
            },
            sampleId = list.fields.firstOrNull { it.path == id }?.sample,
        )
        return Analysis(lists, list, plan, detailMode)
    }

    /** `shows` from `…/shows`, the list's name (`results`) only when the address has no better one. */
    private fun sourceName(url: String, listPath: String): String {
        val segment = runCatching { URI(url).path }.getOrNull().orEmpty().trimEnd('/').substringAfterLast('/')
        val name = segment.ifEmpty { listPath.substringAfterLast('.') }.replace(Regex("[^A-Za-z0-9_]"), "_").trim('_')
        return name.ifEmpty { "api" }.let { if (it.first().isDigit()) "api_$it" else it }
    }
}

private class Analysis(
    val lists: List<ApiShape.ItemList>,
    val list: ApiShape.ItemList,
    val plan: WizardScreens.Plan,
    val detailMode: String,
) {
    fun problems(screens: ScreenStore): List<String> = buildList {
        if (plan.title.isEmpty()) add("Pick the field shown as the title.")
        if (plan.detailUrl != null &&
            plan.id == null
        ) {
            add("A detail address needs an id field; pick one, or let the list pass its fields.")
        }
        if (plan.detailUrl != null &&
            "{{params.id}}" !in plan.detailUrl
        ) {
            add("The detail address must contain {{params.id}}.")
        }
        if (!SOURCE.matches(
                plan.source,
            )
        ) {
            add("The data source name may use letters, digits and _ (starting with a letter).")
        }
        for (id in listOf(plan.listScreen, plan.detailScreen)) {
            if (!SCREEN_ID.matches(id)) add("Screen id '$id': use a–z, 0–9, '-' and '_'.")
            if (screens.get(id) != null) add("A screen '$id' already exists: choose another id.")
        }
        if (plan.listScreen == plan.detailScreen) add("The list and the detail screen need different ids.")
        rawSecret()?.let(::add)
    }

    /** ADR 0008 / T-031: keys never go into screen JSON — they are `{{secret.NAME}}` from `.env`. */
    private fun rawSecret(): String? {
        val inHeader = plan.headers.values.any { it.isNotEmpty() && "{{secret." !in it }
        val inQuery = (
            plan.url.substringAfter('?', "").split('&') +
                (plan.detailUrl?.substringAfter('?', "")?.split('&') ?: emptyList())
            )
            .any { pair -> pair.substringBefore('=').lowercase() in KEY_PARAMS && "{{secret." !in pair }
        if (!inHeader && !inQuery) return null
        return "Keys must not be stored in screens: put the key into .env as COMPOSECAST_SECRET_MYKEY=… (restart the " +
            "server) and write {{secret.MYKEY}} instead of it, in the header value or the address."
    }

    companion object {
        private val SOURCE = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
        private val SCREEN_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
        private val KEY_PARAMS =
            setOf("key", "apikey", "api_key", "api-key", "token", "access_token", "appid", "secret", "auth")
    }
}

private suspend fun ApplicationCall.wizardPage(
    form: WizardForm,
    result: ApiProbe.Result?,
    problems: List<String>,
    analysis: Analysis? = null,
) {
    val fields = analysis?.list?.fields.orEmpty()
    respondTemplate(
        "admin/wizard.ftl",
        page("Create from an API") + mapOf(
            "url" to form.url,
            "headerName" to form["headerName"],
            "headerValue" to form["headerValue"],
            "problems" to problems,
            "probed" to (analysis != null),
            "status" to (result?.status?.toString() ?: ""),
            "millis" to (result?.millis?.toString() ?: ""),
            "lists" to
                (
                    analysis?.lists.orEmpty().map {
                        mapOf(
                            "path" to it.path,
                            "label" to (it.path.ifEmpty { "(the response itself)" }),
                            "size" to it.size,
                        )
                    }
                    ),
            "list" to (analysis?.list?.path ?: ""),
            "fields" to fields.map { mapOf("path" to it.path, "kind" to it.kind.name, "sample" to it.sample.take(90)) },
            "fieldPaths" to fields.map { it.path },
            "plan" to (
                analysis?.plan?.let { plan ->
                    mapOf(
                        "source" to plan.source,
                        "id" to plan.id.orEmpty(),
                        "title" to plan.title,
                        "subtitle" to plan.subtitle.orEmpty(),
                        "image" to plan.image.orEmpty(),
                        "listScreen" to plan.listScreen,
                        "listTitle" to plan.listTitle,
                        "detailScreen" to plan.detailScreen,
                        "detailUrl" to (plan.detailUrl ?: form["detailUrl"]),
                        "detailMode" to analysis.detailMode,
                    )
                } ?: emptyMap<String, String>()
                ),
        ),
    )
}

private fun String.urlEncoded() = URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
