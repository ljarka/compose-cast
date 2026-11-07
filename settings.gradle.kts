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
include(":sdk-android")
include(":app-template")
include(":compat-tests")
