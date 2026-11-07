plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.remote.creation.jvm)
    implementation(libs.remote.creation.core)
    testImplementation(libs.kotlin.test)
}
