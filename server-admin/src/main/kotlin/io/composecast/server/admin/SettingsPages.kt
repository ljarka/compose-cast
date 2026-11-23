package io.composecast.server.admin

import io.composecast.protocol.BrandConfig
import io.composecast.server.store.SettingsStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Base64
import javax.imageio.ImageIO

/**
 * App settings (T-007 A5): the brand (served by `/brand`, used by every screen; saving rebuilds the
 * screens through [ScreenPublishing.publishBrand]) and what an app build needs later (T-010:
 * name, applicationId, server URL, logo), kept in `app_settings`. Admins only may change them.
 */
internal fun Route.settingsPages(settings: SettingsStore, publishing: ScreenPublishing) {
    route("/settings") {
        get {
            val form = SettingsForm.current(settings)
            call.settingsPage(settings, form, problems = emptyList(), saved = "saved" in call.request.queryParameters)
        }
        post {
            if (call.adminUser.role != Role.ADMIN) return@post call.respond(HttpStatusCode.Forbidden)
            val (form, problems) = try {
                SettingsForm.receive(call, SettingsForm.current(settings))
            } catch (e: Exception) { // a body over the limit or a broken upload
                SettingsForm.current(settings) to listOf("logo: could not read the upload (${e.message})")
            }
            val brandJson = Json.encodeToString(BrandConfig.serializer(), form.brand)
            val failure = if (problems.isEmpty()) {
                try {
                    publishing.publishBrand(brandJson)
                    null
                } catch (e: PublishException) {
                    "Not saved, nothing changed: ${e.message}"
                }
            } else {
                null
            }
            if (problems.isNotEmpty() || failure != null) {
                call.response.status(HttpStatusCode.UnprocessableEntity)
                return@post call.settingsPage(settings, form, problems + listOfNotNull(failure), saved = false)
            }
            form.saveAppSettings(settings)
            call.seeOther("/admin/settings?saved")
        }
        get("/logo") {
            val logo =
                settings[AppKeys.LOGO]?.takeIf { it.isNotEmpty() }?.let(Base64.getDecoder()::decode)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondBytes(logo, ContentType.parse(settings[AppKeys.LOGO_TYPE] ?: "image/png"))
        }
    }
}

/** `app_settings` keys of the app build (T-010 reads them; names follow `compose-cast.properties`). */
internal object AppKeys {
    const val NAME = "app.name"
    const val APPLICATION_ID = "app.applicationId"
    const val SERVER_URL = "app.serverUrl"

    /** Base64 of the uploaded PNG/JPEG (for the launcher icon, T-010). */
    const val LOGO = "app.icon"
    const val LOGO_TYPE = "app.icon.type"
}

private const val MAX_LOGO_BYTES = 1024 * 1024

/** Same rule as the `io.composecast.app` Gradle plugin (`ComposeCastAppConfig`). */
private val APPLICATION_ID = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")

/** The settings form: the brand plus the app's build settings; [logo] null = keep, empty = remove. */
private data class SettingsForm(
    val brand: BrandConfig,
    val applicationId: String,
    val serverUrl: String,
    val logo: Logo?,
) {
    class Logo(val bytes: ByteArray, val type: String)

    fun saveAppSettings(settings: SettingsStore) {
        settings[AppKeys.NAME] = brand.name
        settings[AppKeys.APPLICATION_ID] = applicationId
        settings[AppKeys.SERVER_URL] = serverUrl
        when {
            logo == null -> Unit
            logo.bytes.isEmpty() -> {
                settings[AppKeys.LOGO] = ""
                settings[AppKeys.LOGO_TYPE] = ""
            }
            else -> {
                settings[AppKeys.LOGO] = Base64.getEncoder().encodeToString(logo.bytes)
                settings[AppKeys.LOGO_TYPE] = logo.type
            }
        }
    }

    companion object {
        /** Defaults when no brand was imported: the renderer's built-in look. */
        private val DEFAULT_BRAND = BrandConfig(name = "My app", primaryColor = "#6200EE", secondaryColor = "#03DAC6")

        fun current(settings: SettingsStore) = SettingsForm(
            brand = settings[SettingsStore.BRAND]?.let { Json.decodeFromString(BrandConfig.serializer(), it) }
                ?: DEFAULT_BRAND.copy(name = settings[AppKeys.NAME] ?: DEFAULT_BRAND.name),
            applicationId = settings[AppKeys.APPLICATION_ID].orEmpty(),
            serverUrl = settings[AppKeys.SERVER_URL].orEmpty(),
            logo = null,
        )

        /** Reads the multipart form; returns it with every problem, each starting with the field name. */
        suspend fun receive(call: ApplicationCall, current: SettingsForm): Pair<SettingsForm, List<String>> {
            val fields = mutableMapOf<String, String>()
            var upload: ByteArray? = null
            call.receiveMultipart(formFieldLimit = MAX_LOGO_BYTES + 64L * 1024).forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> part.name?.let { fields[it] = part.value.trim() }
                    is PartData.FileItem -> if (part.name == "logo") {
                        upload = part.provider().readRemaining(MAX_LOGO_BYTES + 1L).readByteArray()
                    }
                    else -> Unit
                }
                part.dispose()
            }
            fun optional(name: String) = fields[name]?.takeIf { it.isNotEmpty() }
            val brand = current.brand.copy(
                name = fields["name"].orEmpty(),
                primaryColor = fields["primaryColor"].orEmpty(),
                secondaryColor = fields["secondaryColor"].orEmpty(),
                backgroundColor = fields["backgroundColor"].orEmpty(),
                surfaceColor = optional("surfaceColor"),
                textColor = optional("textColor"),
                outlineColor = optional("outlineColor"),
            )
            val problems = brand.problems().toMutableList()
            val applicationId = fields["applicationId"].orEmpty()
            if (applicationId.isNotEmpty() && !APPLICATION_ID.matches(applicationId)) {
                problems += "applicationId: expected a package name like com.example.app, was '$applicationId'"
            }
            val serverUrl = fields["serverUrl"].orEmpty().trimEnd('/')
            serverUrlProblem(serverUrl)?.let { problems += "serverUrl: $it" }
            val logo = when {
                fields["removeLogo"] == "on" -> Logo(ByteArray(0), "")
                upload == null || upload!!.isEmpty() -> null
                else -> logo(upload!!).also { (_, problem) -> problem?.let { problems += "logo: $it" } }.first
            }
            return SettingsForm(brand, applicationId, serverUrl, logo) to problems
        }

        private fun serverUrlProblem(url: String): String? {
            if (url.isEmpty()) return null
            val uri = runCatching { URI(url) }.getOrNull()
            return when {
                uri == null || uri.host.isNullOrEmpty() -> "expected a URL like https://app.example.com, was '$url'"
                uri.scheme !in setOf("http", "https") -> "must start with https:// (or http:// for local testing)"
                else -> null
            }
        }

        /** PNG or JPEG that decodes, at most 1 MiB. */
        private fun logo(bytes: ByteArray): Pair<Logo?, String?> {
            if (bytes.size > MAX_LOGO_BYTES) return null to "larger than 1 MB"
            val type = when {
                bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
                bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
                else -> return null to "must be a PNG or JPEG image"
            }
            val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
                ?: return null to "the image could not be read"
            if (image.width != image.height) return null to "must be square (was ${image.width}×${image.height})"
            return Logo(bytes, type) to null
        }

        private fun ByteArray.startsWith(vararg prefix: Int) =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
    }
}

private suspend fun ApplicationCall.settingsPage(
    settings: SettingsStore,
    form: SettingsForm,
    problems: List<String>,
    saved: Boolean,
) = respondTemplate(
    "admin/settings.ftl",
    page("Settings") + mapOf(
        "isAdmin" to (adminUser.role == Role.ADMIN),
        "brand" to form.brand,
        "applicationId" to form.applicationId,
        "serverUrl" to form.serverUrl,
        "hasLogo" to !settings[AppKeys.LOGO].isNullOrEmpty(),
        "problems" to problems,
        "saved" to saved,
    ),
)
