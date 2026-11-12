pluginManagement {
    // The io.composecast.app Gradle plugin (T-024), used by app-template. Not in the server-only
    // Docker build, whose context does not contain gradle-plugins/.
    if (providers.gradleProperty("composecast.serverOnly").orNull != "true") {
        includeBuild("gradle-plugins")
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "compose-cast"

include(":protocol")
include(":sdk-server")
include(":server-scripting")
include(":server-app")

// `-Pcomposecast.serverOnly=true` (server-app/Dockerfile): JVM modules only, so the server image
// builds without an Android SDK.
if (providers.gradleProperty("composecast.serverOnly").orNull != "true") {
    include(":sdk-android")
    include(":app-shell")
    include(":app-template")
    include(":compat-tests")
}
