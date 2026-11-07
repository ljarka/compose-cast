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
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kotlinx.serialization.json)
    // SLF4J provider: without it Ktor and CallLogging print nothing (not even 500 stack traces).
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.kotlin.test)
}

// Relative COMPOSECAST_DATA_DIR paths (e.g. examples/demo) resolve against the repository root.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.test {
    // JsonScreensTest loads examples/demo as a data directory.
    systemProperty("composecast.examples", rootProject.projectDir.resolve("examples").path)
}
