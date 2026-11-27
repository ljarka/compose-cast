// Top-level build file
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

// Published artifacts (T-024, ADR 0019): io.composecast:<module>:<composecastVersion>. Modules opt in
// by applying `maven-publish`. Without -PcomposecastMavenUrl, `publish` writes to build/maven-dry-run
// (verify.sh, CI dry run); the release workflow passes the Reposilite URL and credentials
// (composecastUsername / composecastPassword). gradle-plugins/build.gradle.kts mirrors this block.
val composecastVersion = providers.gradleProperty("composecastVersion").getOrElse("0.1.0-SNAPSHOT")
val composecastMavenUrl = providers.gradleProperty("composecastMavenUrl").orNull
val dryRunRepository = layout.buildDirectory.dir("maven-dry-run")
subprojects {
    group = "io.composecast"
    version = composecastVersion
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "composecast"
                    if (composecastMavenUrl != null) {
                        url = uri(composecastMavenUrl)
                        // Plain http only for a local Reposilite (tests, docs/playbooks/maven-repository.md).
                        isAllowInsecureProtocol =
                            Regex("^http://(localhost|127\\.0\\.0\\.1)[:/]").containsMatchIn(composecastMavenUrl)
                        credentials(PasswordCredentials::class)
                        authentication { create<BasicAuthentication>("basic") }
                    } else {
                        url = uri(dryRunRepository)
                    }
                }
            }
        }
    }
}

// Formatting: ktlint via Spotless, configured once here for the whole repository (rules in
// .editorconfig). `./gradlew spotlessApply` fixes, `spotlessCheck` (in scripts/verify.sh) fails.
// File globs instead of per-module setup: works the same for JVM and AGP 9 (built-in Kotlin) modules.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}

// Static analysis (T-055): detekt on every Kotlin module, configured once here. Rules in
// config/detekt/detekt.yml. `./gradlew detekt` runs it (verify.sh fails on violations).
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        allRules = false
    }
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports.html.required = false
        reports.sarif.required = false
        reports.xml.required = false
    }
}
