plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("io.composecast.server.MainKt")
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":sdk-server"))
    implementation(project(":server-scripting"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}
