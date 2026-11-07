plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":sdk-server"))
    testImplementation(libs.kotlin.test)
}
