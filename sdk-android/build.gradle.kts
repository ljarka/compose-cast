plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.composecast.android"
    compileSdk = 35
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
    lint {
        // Remote Compose alpha20 has no public player API: everything is
        // @RestrictTo(LIBRARY_GROUP). Using it is a deliberate decision confined to this module
        // (see AGENTS.md), so it is a warning here and stays an error everywhere else.
        warning += "RestrictedApi"
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.remote.core)
    implementation(libs.remote.player.core)
    implementation(libs.remote.player.view)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    testImplementation(libs.kotlin.test)
}
