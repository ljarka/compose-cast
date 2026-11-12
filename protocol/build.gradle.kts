plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

java { withSourcesJar() }

publishing {
    publications {
        register<MavenPublication>("maven") { from(components["java"]) }
    }
}

dependencies {
    // @Serializable types are public API; consumers need the runtime to (de)serialize them.
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}
