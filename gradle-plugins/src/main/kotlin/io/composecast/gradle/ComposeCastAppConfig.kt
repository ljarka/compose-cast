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
    val signing: Signing? = null,
    /**
     * `app.preview=true`: the generic compose-cast Preview app (T-045, ADR 0024) — the server URL is
     * chosen at runtime (`composecast://connect` link or typed in), so `app.serverUrl` is optional,
     * and plain HTTP is allowed in every build. Never for a branded app.
     */
    val preview: Boolean = false,
) {
    /**
     * Release signing (T-044): keystore and alias from the properties file; passwords only from env
     * `COMPOSECAST_SIGNING_STORE_PASSWORD` / `COMPOSECAST_SIGNING_KEY_PASSWORD` or Gradle properties
     * `composecast.signing.storePassword` / `composecast.signing.keyPassword` — never from the file.
     * Missing passwords fail only release builds (null here), so debug builds work without them.
     */
    data class Signing(val storeFile: File, val keyAlias: String, val storePassword: String?, val keyPassword: String?)

    companion object {
        private val colorPattern = Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")
        private val applicationIdPattern = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")

        const val STORE_PASSWORD_ENV = "COMPOSECAST_SIGNING_STORE_PASSWORD"
        const val KEY_PASSWORD_ENV = "COMPOSECAST_SIGNING_KEY_PASSWORD"
        const val STORE_PASSWORD_PROPERTY = "composecast.signing.storePassword"
        const val KEY_PASSWORD_PROPERTY = "composecast.signing.keyPassword"
        private val SECRET_KEYS = listOf("app.signing.storePassword", "app.signing.keyPassword", "app.signing.password")

        /** @param secret looks up a signing password by env name or Gradle property name (first non-null wins) */
        fun load(file: File, secret: (String) -> String? = { null }): ComposeCastAppConfig {
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
            val preview = when (val value = optional("app.preview")) {
                null, "false" -> false
                "true" -> true
                else -> invalid("app.preview", "must be true or false (is '$value')")
            }
            val serverUrl = (
                if (preview) {
                    optional(
                        "app.serverUrl",
                    ).orEmpty()
                } else {
                    required("app.serverUrl")
                }
                ).trimEnd('/')
            if (!(preview && serverUrl.isEmpty()) && !serverUrl.startsWith("http://") &&
                !serverUrl.startsWith("https://")
            ) {
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
            SECRET_KEYS.firstOrNull { props.getProperty(it) != null }?.let {
                invalid(it, "must not be in the file (passwords come from $STORE_PASSWORD_ENV / $KEY_PASSWORD_ENV)")
            }
            val storeFile = optional("app.signing.storeFile")
            val keyAlias = optional("app.signing.keyAlias")
            val signing = when {
                storeFile == null && keyAlias == null -> null
                storeFile == null -> throw GradleException(
                    "Missing key 'app.signing.storeFile' in ${file.path} (app.signing.keyAlias is set)",
                )
                keyAlias == null -> throw GradleException(
                    "Missing key 'app.signing.keyAlias' in ${file.path} (app.signing.storeFile is set)",
                )
                else -> Signing(
                    storeFile = file.absoluteFile.parentFile.resolve(storeFile).also {
                        if (!it.isFile) invalid("app.signing.storeFile", "file not found: ${it.path}")
                    },
                    keyAlias = keyAlias,
                    storePassword = secret(STORE_PASSWORD_ENV) ?: secret(STORE_PASSWORD_PROPERTY),
                    keyPassword = secret(KEY_PASSWORD_ENV) ?: secret(KEY_PASSWORD_PROPERTY),
                )
            }
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
                signing = signing,
                preview = preview,
            )
        }
    }
}
