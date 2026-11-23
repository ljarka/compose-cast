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
    implementation(libs.qrcodegen) // T-045 B2: QR codes as inline SVG
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    // FormSchemaTest round-trips every component of examples/demo through the generated forms.
    systemProperty("composecast.examples", rootProject.projectDir.resolve("examples").path)
}
