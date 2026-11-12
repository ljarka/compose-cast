package io.composecast.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

/**
 * `io.composecast.app`: turns a project with only `compose-cast.properties` (and an optional icon)
 * into a branded compose-cast Android app (T-011, T-024, ADR 0018). Applies `com.android.application`,
 * configures it from the properties file and adds `io.composecast:app-shell`, which contains the
 * whole app; the app project needs no Kotlin, manifest or resources.
 *
 * The properties file is `compose-cast.properties` in the project directory, or
 * `-Pcomposecast.appConfig=<path>` (relative to the root project).
 */
class ComposeCastAppPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.android.application")

        val configFile = project.providers.gradleProperty("composecast.appConfig").orNull
            ?.let { project.rootProject.file(it) }
            ?: project.file("compose-cast.properties")
        val config = ComposeCastAppConfig.load(configFile)

        project.extensions.configure(ApplicationExtension::class.java) { android ->
            if (android.namespace == null) android.namespace = config.applicationId
            android.compileSdk = COMPILE_SDK
            android.defaultConfig.apply {
                applicationId = config.applicationId
                minSdk = MIN_SDK
                targetSdk = TARGET_SDK
                versionCode = config.versionCode
                versionName = config.versionName
                // App resources override app-shell's defaults (composecast_config.xml).
                resValue("string", "app_name", config.name)
                resValue("string", "composecast_server_url", config.serverUrl)
                resValue("color", "composecast_frame_primary", config.framePrimaryColor)
                resValue("color", "composecast_frame_background", config.frameBackgroundColor)
            }
            android.buildFeatures.resValues = true
            // AGP needs a main manifest; everything real is in app-shell's (merged in). An app
            // project may still bring its own src/main/AndroidManifest.xml.
            val main = android.sourceSets.getByName("main")
            if (!project.file("src/main/AndroidManifest.xml").isFile) {
                val manifest = project.layout.buildDirectory.file(
                    "generated/composecast/AndroidManifest.xml",
                ).get().asFile
                manifest.parentFile.mkdirs()
                manifest.writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />\n")
                main.manifest.srcFile(manifest)
            }
        }

        val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

        config.icon?.let { iconFile ->
            val generateIcon = project.tasks.register(
                "generateComposeCastLauncherIcon",
                GenerateLauncherIconTask::class.java,
            ) {
                it.icon.set(iconFile)
                it.backgroundColor.set(config.iconBackgroundColor)
                it.outputDir.set(project.layout.buildDirectory.dir("generated/composecast/icon"))
            }
            androidComponents.onVariants { variant ->
                variant.sources.res?.addGeneratedSourceDirectory(generateIcon, GenerateLauncherIconTask::outputDir)
            }
        }

        // Plain HTTP only in debug builds and only to local dev hosts; every other build type
        // needs an https:// server, checked before it builds (debug builds stay usable meanwhile).
        val networkConfig = project.tasks.register(
            "generateComposeCastDebugNetworkConfig",
            GenerateDebugNetworkConfigTask::class.java,
        ) {
            it.outputDir.set(project.layout.buildDirectory.dir("generated/composecast/debugNetwork"))
        }
        androidComponents.onVariants { variant ->
            val variantName = variant.name.replaceFirstChar(Char::uppercase)
            if (variant.buildType == "debug") {
                variant.sources.res?.addGeneratedSourceDirectory(
                    networkConfig,
                    GenerateDebugNetworkConfigTask::outputDir,
                )
                return@onVariants
            }
            val serverUrl = config.serverUrl
            val source = configFile.path
            val check = project.tasks.register("check${variantName}ComposeCastConfig") { task ->
                task.doLast {
                    if (!serverUrl.startsWith("https://")) {
                        throw GradleException(
                            "app.serverUrl must start with https:// for ${variant.name} builds (is '$serverUrl' in $source).",
                        )
                    }
                }
            }
            project.tasks.matching { it.name == "pre${variantName}Build" }.configureEach { it.dependsOn(check) }
        }

        // Inside the compose-cast repository the shell is a project; everywhere else the published
        // artifact in the plugin's own version.
        val shell: Any = project.rootProject.findProject(":app-shell")
            ?.let { project.dependencies.project(mapOf("path" to ":app-shell")) }
            ?: "io.composecast:app-shell:$pluginVersion"
        project.dependencies.add("implementation", shell)
    }

    private companion object {
        const val COMPILE_SDK = 37
        const val MIN_SDK = 26
        const val TARGET_SDK = 36

        val pluginVersion: String by lazy {
            val props = Properties()
            ComposeCastAppPlugin::class.java.getResourceAsStream("/io/composecast/gradle/plugin.properties")
                ?.use(props::load)
            props.getProperty("version") ?: throw GradleException("io.composecast.app: plugin version resource missing")
        }
    }
}
