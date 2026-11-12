import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Everything that differs between apps built from this template comes from one file (T-011):
// `compose-cast.properties` next to this script, or another file via `-Pcomposecast.appConfig=<path>`.
val appConfigFile: File = providers.gradleProperty("composecast.appConfig").orNull
    ?.let { rootProject.file(it) }
    ?: file("compose-cast.properties")
val appConfig = ComposeCastAppConfig.load(appConfigFile)

android {
    // Code package; stays the same for every app. Only applicationId changes.
    namespace = "io.composecast.demo"
    compileSdk = 37
    defaultConfig {
        applicationId = appConfig.applicationId
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        resValue("string", "app_name", appConfig.name)
        resValue("color", "composecast_frame_primary", appConfig.framePrimaryColor)
        resValue("color", "composecast_frame_background", appConfig.frameBackgroundColor)
        buildConfigField("String", "SERVER_URL", "\"${appConfig.serverUrl}\"")
        buildConfigField("int", "FRAME_PRIMARY_COLOR", appConfig.framePrimaryColor.toArgbLiteral())
        buildConfigField("int", "FRAME_BACKGROUND_COLOR", appConfig.frameBackgroundColor.toArgbLiteral())
        manifestPlaceholders["appIcon"] =
            if (appConfig.icon != null) "@mipmap/ic_launcher_custom" else "@mipmap/ic_launcher"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

// Custom launcher icon: adaptive-icon foreground PNGs per density, generated from `app.icon`.
appConfig.icon?.let { iconFile ->
    val generateIcon = tasks.register<GenerateLauncherIconTask>("generateComposeCastLauncherIcon") {
        icon.set(iconFile)
        backgroundColor.set(appConfig.iconBackgroundColor)
        outputDir.set(layout.buildDirectory.dir("generated/composecast/icon"))
    }
    androidComponents.onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateIcon, GenerateLauncherIconTask::outputDir)
    }
}

// Release builds talk to the server over https only (cleartext is allowed for local debug hosts only,
// src/debug/res/xml/network_security_config.xml). Checked when a release variant is built, so debug
// builds with a local http server keep working.
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val serverUrl = appConfig.serverUrl
    val source = appConfigFile.path
    val check = tasks.register("check${variant.name.replaceFirstChar(Char::uppercase)}ComposeCastConfig") {
        doLast {
            if (!serverUrl.startsWith("https://")) {
                throw GradleException(
                    "app.serverUrl must start with https:// for release builds (is '$serverUrl' in $source).",
                )
            }
        }
    }
    tasks.named { it == "pre${variant.name.replaceFirstChar(Char::uppercase)}Build" }
        .configureEach { dependsOn(check) }
}

dependencies {
    implementation(project(":sdk-android"))
    implementation(project(":protocol"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}

// --- compose-cast app configuration ------------------------------------------------------------
// Self-contained (no app-template paths): T-024 moves this unchanged into the `io.composecast.app`
// Gradle plugin.

/** Values of `compose-cast.properties`; [load] fails the build naming the missing or invalid key. */
data class ComposeCastAppConfig(
    val name: String,
    val applicationId: String,
    val serverUrl: String,
    val framePrimaryColor: String,
    val frameBackgroundColor: String,
    val icon: File?,
    val iconBackgroundColor: String,
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
            fun color(key: String, value: String): String {
                if (!colorPattern.matches(value)) {
                    throw GradleException("Key '$key' in ${file.path} must be #RRGGBB or #AARRGGBB (is '$value')")
                }
                return value.uppercase()
            }

            val applicationId = required("app.applicationId")
            if (!applicationIdPattern.matches(applicationId)) {
                throw GradleException(
                    "Key 'app.applicationId' in ${file.path} is not a valid application id: '$applicationId'",
                )
            }
            val serverUrl = required("app.serverUrl").trimEnd('/')
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                throw GradleException(
                    "Key 'app.serverUrl' in ${file.path} must start with http:// or https:// (is '$serverUrl')",
                )
            }
            val framePrimary = color("app.frame.primaryColor", required("app.frame.primaryColor"))
            val icon = optional("app.icon")?.let { path ->
                file.parentFile.resolve(path).also {
                    if (!it.isFile) throw GradleException("Key 'app.icon' in ${file.path}: file not found: ${it.path}")
                }
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
            )
        }
    }
}

/** `#RRGGBB` / `#AARRGGBB` → Java int literal (`0xFFRRGGBB`) for a `BuildConfig` field. */
fun String.toArgbLiteral(): String = "0x" + removePrefix("#").let { if (it.length == 6) "FF$it" else it }

/**
 * Writes an adaptive launcher icon `@mipmap/ic_launcher_custom`: the image is scaled to fit the
 * 72dp safe zone of the 108dp foreground layer, on a solid background color.
 */
abstract class GenerateLauncherIconTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val icon: RegularFileProperty

    @get:Input
    abstract val backgroundColor: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val source = ImageIO.read(icon.get().asFile)
            ?: throw GradleException("app.icon is not a readable PNG/JPG: ${icon.get().asFile}")
        // Foreground layer is 108dp, of which the central 72dp is always visible.
        mapOf(
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0,
        ).forEach { (density, scale) ->
            val canvas = (108 * scale).toInt()
            val safe = 72 * scale
            val fit = minOf(safe / source.width, safe / source.height)
            val w = (source.width * fit).toInt()
            val h = (source.height * fit).toInt()
            val image = BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(source, (canvas - w) / 2, (canvas - h) / 2, w, h, null)
                dispose()
            }
            val dir = out.resolve("mipmap-$density").apply { mkdirs() }
            ImageIO.write(image, "png", dir.resolve("ic_launcher_custom_foreground.png"))
        }
        out.resolve("values").apply { mkdirs() }.resolve("ic_launcher_custom_colors.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="ic_launcher_custom_background">${backgroundColor.get()}</color>
            </resources>
            """.trimIndent() + "\n",
        )
        out.resolve("mipmap-anydpi").apply { mkdirs() }.resolve("ic_launcher_custom.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@color/ic_launcher_custom_background" />
                <foreground android:drawable="@mipmap/ic_launcher_custom_foreground" />
            </adaptive-icon>
            """.trimIndent() + "\n",
        )
    }
}
