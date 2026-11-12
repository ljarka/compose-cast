// compose-cast Android app repository (T-024): copy this directory and edit compose-cast.properties
// (docs/playbooks/new-app-repository.md). maven.jarka.club hosts compose-cast's artifacts.

pluginManagement {
    repositories {
        maven("https://maven.jarka.club/releases")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.jarka.club/releases")
        google()
        mavenCentral()
    }
}

rootProject.name = "my-app"
