plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":sdk-server"))
    testImplementation(libs.kotlin.test)
}
