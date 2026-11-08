pluginManagement {
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
    include(":app-template")
    include(":compat-tests")
}
