import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
}

group = "io.composecast"
version = providers.gradleProperty("composecastVersion").getOrElse("0.1.0-SNAPSHOT")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Runs on Gradle's embedded Kotlin runtime, which is older than the compiler: do not use
        // newer stdlib API.
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }
}

dependencies {
    // The plugin applies com.android.application, so AGP comes with it (app repositories declare
    // only this plugin).
    implementation(libs.android.gradle)
    testImplementation(libs.kotlin.test)
}

gradlePlugin {
    plugins {
        register("app") {
            id = "io.composecast.app"
            implementationClass = "io.composecast.gradle.ComposeCastAppPlugin"
            displayName = "compose-cast Android app"
            description = "Builds a branded compose-cast Android app from compose-cast.properties"
        }
    }
}

// The plugin adds io.composecast:app-shell in its own version.
val generateVersion = tasks.register("generateComposeCastVersion") {
    val out = layout.buildDirectory.dir("generated/composecast-version")
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    outputs.dir(out)
    doLast {
        out.get().file("io/composecast/gradle/plugin.properties").asFile.apply {
            parentFile.mkdirs()
            writeText("version=$pluginVersion\n")
        }
    }
}
sourceSets.main { resources.srcDir(generateVersion) }

// Same repository setup as the main build (root build.gradle.kts): dry run into the main build's
// build/maven-dry-run unless -PcomposecastMavenUrl is given.
publishing {
    repositories {
        maven {
            name = "composecast"
            val remote = providers.gradleProperty("composecastMavenUrl").orNull
            if (remote != null) {
                url = uri(remote)
                // Plain http only for a local Reposilite (tests, docs/playbooks/maven-repository.md).
                isAllowInsecureProtocol = Regex("^http://(localhost|127\\.0\\.0\\.1)[:/]").containsMatchIn(remote)
                credentials(PasswordCredentials::class)
                authentication { create<BasicAuthentication>("basic") }
            } else {
                url = uri(rootDir.resolve("../build/maven-dry-run"))
            }
        }
    }
}
