plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":server-store"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.freemarker)
    implementation(libs.bcrypt)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
}
