// compose-cast Android app repository (T-024): copy this directory, replace the Maven URL with your
// compose-cast Maven repository (both places; docs/playbooks/new-app-repository.md), edit
// compose-cast.properties.

pluginManagement {
    repositories {
        maven("https://maven.example.com/releases")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.example.com/releases")
        google()
        mavenCentral()
    }
}

rootProject.name = "my-app"
