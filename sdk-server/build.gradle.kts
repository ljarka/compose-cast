plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // BrandConfig and action constants are part of this module's public API.
    api(project(":protocol"))
    // Remote Compose stays an implementation detail (see RemoteComposeRenderer).
    implementation(libs.remote.creation.core)
    implementation(libs.remote.creation.jvm)
    implementation(libs.remote.core)
    testImplementation(libs.kotlin.test)
}
