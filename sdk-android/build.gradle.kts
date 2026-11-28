plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

// Published libraries (T-055): explicit public API.
kotlin { explicitApi() }

android {
    namespace = "io.composecast.android"
    compileSdk = 37
    defaultConfig {
        minSdk = 26 // required by remote-player-*
        // R8 rules for apps that minify (T-058): packaged into the AAR as proguard.txt.
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    lint {
        // Remote Compose alpha20 has no public player API: everything is
        // @RestrictTo(LIBRARY_GROUP). Using it is a deliberate decision confined to this module
        // (see AGENTS.md), so it is a warning here and stays an error everywhere else.
        warning += "RestrictedApi"
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
    api(project(":protocol")) // Action is part of the public API (ComposeCastScreen)
    implementation(libs.remote.core)
    implementation(libs.remote.player.core)
    implementation(libs.remote.player.view)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.lifecycle.runtime.compose) // live screens: only while the app is started
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    // liveScreen over a real WebSocket (MockEngine has no WebSocket support).
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.websockets)
}
