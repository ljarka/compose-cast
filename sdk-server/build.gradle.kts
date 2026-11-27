plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Published libraries (T-055): explicit public API.
kotlin { explicitApi() }

dependencies {
    // BrandConfig and action constants are part of this module's public API.
    api(project(":protocol"))
    // Remote Compose stays an implementation detail (see RemoteComposeRenderer).
    implementation(libs.remote.creation.core)
    implementation(libs.remote.creation.jvm)
    implementation(libs.remote.core)
    // RemoteComposeJsonParser (custom components, ADR 0033) needs org.json, absent on a plain JVM.
    implementation(libs.org.json)
    testImplementation(libs.kotlin.test)
}

// The Remote Compose release custom component bodies are validated against (ADR 0033).
val rcVersion = libs.versions.remote.compose.get()
tasks.processResources {
    inputs.property("rcVersion", rcVersion)
    filesMatching("io/composecast/server/sdk/rc-version.properties") { expand("rcVersion" to rcVersion) }
}
