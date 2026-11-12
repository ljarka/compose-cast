import java.util.Properties

val appProps = Properties().also { props ->
    file("compose-cast.properties").inputStream().use { props.load(it) }
}

fun appProp(key: String): String =
    appProps.getProperty(key) ?: error("Missing required key '$key' in compose-cast.properties")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.composecast.demo"
    compileSdk = 37
    defaultConfig {
        applicationId = appProp("app.applicationId")
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        resValue("string", "app_name", appProp("app.name"))
        buildConfigField("String", "SERVER_URL", "\"${appProp("app.serverUrl")}\"")
        buildConfigField("String", "FRAME_PRIMARY_COLOR", "\"${appProp("app.frame.primaryColor")}\"")
        buildConfigField("String", "FRAME_BACKGROUND_COLOR", "\"${appProp("app.frame.backgroundColor")}\"")
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

if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
    val serverUrl = appProp("app.serverUrl")
    if (serverUrl.startsWith("http://")) {
        throw GradleException(
            "Release build blocked: app.serverUrl must use https:// " +
                "(got '$serverUrl' in compose-cast.properties)",
        )
    }
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
