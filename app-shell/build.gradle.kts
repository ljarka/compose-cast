// The app shell (T-024): the activity every compose-cast Android app runs, published as
// io.composecast:app-shell. Apps contain no Kotlin; the io.composecast.app Gradle plugin adds this
// library and configures it through resources (gradle-plugins/AGENTS.md).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "io.composecast.shell"
    compileSdk = 37
    defaultConfig {
        minSdk = 26 // required by remote-player-*
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
        }
    }
}

dependencies {
    implementation(project(":sdk-android"))
    implementation(project(":protocol"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
}
