package io.composecast.gradle

import org.gradle.api.GradleException
import java.io.File
import java.util.Properties

/** Values of `compose-cast.properties` (T-011); [load] fails the build naming the missing or invalid key. */
data class ComposeCastAppConfig(
    val name: String,
    val applicationId: String,
    val serverUrl: String,
    val framePrimaryColor: String,
    val frameBackgroundColor: String,
    val icon: File?,
    val iconBackgroundColor: String,
    val versionCode: Int,
    val versionName: String,
) {
    companion object {
        private val colorPattern = Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")
        private val applicationIdPattern = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")

        fun load(file: File): ComposeCastAppConfig {
            if (!file.isFile) throw GradleException("compose-cast app configuration not found: ${file.path}")
            val props = Properties().apply { file.reader(Charsets.UTF_8).use(::load) }
            fun optional(key: String): String? = props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
            fun required(key: String): String =
                optional(key) ?: throw GradleException("Missing key '$key' in ${file.path}")
            fun invalid(key: String, message: String): Nothing =
                throw GradleException("Key '$key' in ${file.path} $message")
            fun color(key: String, value: String): String {
                if (!colorPattern.matches(value)) invalid(key, "must be #RRGGBB or #AARRGGBB (is '$value')")
                return value.uppercase()
            }

            val applicationId = required("app.applicationId")
            if (!applicationIdPattern.matches(applicationId)) {
                invalid("app.applicationId", "is not a valid application id: '$applicationId'")
            }
            val serverUrl = required("app.serverUrl").trimEnd('/')
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                invalid("app.serverUrl", "must start with http:// or https:// (is '$serverUrl')")
            }
            val framePrimary = color("app.frame.primaryColor", required("app.frame.primaryColor"))
            val icon = optional("app.icon")?.let { path ->
                file.absoluteFile.parentFile.resolve(path).also {
                    if (!it.isFile) invalid("app.icon", "file not found: ${it.path}")
                }
            }
            val versionCode = optional("app.versionCode")?.let {
                it.toIntOrNull()?.takeIf { code -> code > 0 }
                    ?: invalid("app.versionCode", "must be a positive integer (is '$it')")
            } ?: 1
            return ComposeCastAppConfig(
                name = required("app.name"),
                applicationId = applicationId,
                serverUrl = serverUrl,
                framePrimaryColor = framePrimary,
                frameBackgroundColor = color("app.frame.backgroundColor", required("app.frame.backgroundColor")),
                icon = icon,
                iconBackgroundColor = optional("app.icon.backgroundColor")
                    ?.let { color("app.icon.backgroundColor", it) } ?: framePrimary,
                versionCode = versionCode,
                versionName = optional("app.versionName") ?: "1.0",
            )
        }
    }
}
