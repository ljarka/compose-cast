// The in-panel phone preview renderer (T-048): a long-lived JVM process that plays Remote Compose
// documents with sdk-android's real player under Robolectric (native graphics) and returns PNGs.
//
// Robolectric only boots inside a test runner with the Android build's generated test config, so
// the service is this module's unit-test classpath: `RenderServer` is a test that serves HTTP
// until the process is stopped (skipped unless -Dcomposecast.renderer.serve=true), and
// `installRenderer` copies that classpath, the config and the android-all jar into
// build/renderer/ — a directory that runs with plain `java` and no network (Dockerfile).
//
//   ./gradlew :preview-renderer:installRenderer && preview-renderer/build/renderer/bin/preview-renderer
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.composecast.renderer"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

/** The Android runtime Robolectric loads for `@Config(sdk = [35])`, bundled for offline mode. */
val androidAll: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    testImplementation(project(":sdk-android"))
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.foundation)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    // Builds the warm-up document at start-up (always the current format) and test documents.
    testImplementation(project(":sdk-server"))
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
    androidAll(libs.robolectric.android.all)
}

/** The unit-test runtime classpath: compiled test classes, R, AAR classes, AGP's test config dir. */
val testClasspath = files(provider { tasks.named<Test>("testDebugUnitTest").get().classpath })

val installRenderer by tasks.registering {
    group = "distribution"
    description = "Copies a runnable renderer (classpath, Android config, android-all) to build/renderer."
    // The files test_config.properties points at (manifest, resource apk, assets).
    dependsOn("processDebugUnitTestManifest", "packageDebugUnitTestForUnitTest", "mergeDebugUnitTestAssets")
    inputs.files(testClasspath)
    val out = layout.buildDirectory.dir("renderer")
    val projectDir = layout.projectDirectory
    val androidAllFiles = androidAll
    inputs.files(androidAllFiles)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile
        dir.deleteRecursively()
        val lib = dir.resolve("lib").apply { mkdirs() }
        val entries = mutableListOf("config")
        // Robolectric reads com/android/tools/test_config.properties from the classpath; its paths
        // are relative to the working directory, so write one pointing at copies in android/.
        val original = Properties().apply {
            testClasspath.files.map { it.resolve("com/android/tools/test_config.properties") }
                .first { it.isFile }.inputStream().use { load(it) }
        }
        val android = dir.resolve("android").apply { mkdirs() }
        val config = Properties()
        for (key in listOf("android_merged_manifest", "android_resource_apk", "android_merged_assets")) {
            val source = projectDir.file(original.getProperty(key)).asFile
            val target = android.resolve(if (source.isFile) source.name else key.removePrefix("android_"))
            source.copyRecursively(target, overwrite = true)
            config[key] = "android/${target.name}"
        }
        config["android_custom_package"] = original.getProperty("android_custom_package")
        dir.resolve("config/com/android/tools").apply { mkdirs() }
            .resolve("test_config.properties").outputStream().use { config.store(it, "preview-renderer") }
        // The test classpath lists every library jar twice (the Gradle cache copy and a transformed
        // one; the 150 MB native runtime too): keep the first, as the class loader would. Project
        // outputs (several are named classes.jar) are all kept.
        testClasspath.files.filter { it.exists() && !it.resolve("com/android/tools/test_config.properties").isFile }
            .distinctBy { if ("/caches/" in it.invariantSeparatorsPath) it.name else it.path }
            .forEachIndexed { i, file ->
                val name = "%03d-%s".format(i, file.name)
                file.copyRecursively(lib.resolve(name), overwrite = true)
                entries += "lib/$name"
            }
        val deps = dir.resolve("android-all").apply { mkdirs() }
        androidAllFiles.files.forEach { it.copyTo(deps.resolve(it.name), overwrite = true) }
        dir.resolve("classpath.txt").writeText(entries.joinToString(":"))
        val bin = dir.resolve("bin").apply { mkdirs() }.resolve("preview-renderer")
        bin.writeText(
            """
            |#!/bin/sh
            |# Starts the preview renderer (T-048). PORT (default 8090).
            |cd "${'$'}(dirname "${'$'}0")/.." || exit 1
            |exec java ${'$'}JAVA_OPTS -cp "${'$'}(cat classpath.txt)" \
            |  -Drobolectric.offline=true -Drobolectric.dependency.dir=android-all \
            |  -Dcomposecast.renderer.serve=true \
            |  io.composecast.renderer.MainKt
            |
            """.trimMargin(),
        )
        bin.setExecutable(true)
    }
}
