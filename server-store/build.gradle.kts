plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    runtimeOnly(libs.sqlite.jdbc)
    implementation(libs.bcrypt)
    testImplementation(libs.kotlin.test)
}
