// Golden compatibility tests between the server and the Android player:
// demo screens are rendered by sdk-server, played by sdk-android's RemoteComposePlayer under
// Robolectric (no emulator, no network) and compared with screenshots committed in
// src/test/screenshots/ (also shown in the README).
//
//   ./gradlew :compat-tests:recordRoborazziDebug   # update the golden screenshots
//   ./gradlew :compat-tests:verifyRoborazziDebug   # fail if rendering changed (runs in CI)
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.composecast.compat"
    compileSdk = 35
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

dependencies {
    testImplementation(project(":sdk-android"))
    testImplementation(project(":sdk-server"))
    testImplementation(project(":server-app")) // demoScreens()
    testImplementation(libs.compose.material3)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
