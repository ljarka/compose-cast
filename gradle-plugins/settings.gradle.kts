// Included build with compose-cast's Gradle plugins (T-024). The main build includes it through
// pluginManagement.includeBuild; it is also published (io.composecast.app plugin marker + jar).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "gradle-plugins"
