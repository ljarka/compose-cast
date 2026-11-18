// Builds an app repository against locally published compose-cast artifacts instead of
// maven.jarka.club, without editing the app repository (T-025; before the first release, or to try
// unreleased framework changes in a real app):
//
//   ./gradlew -PcomposecastVersion=0.1.0 publish :gradle-plugins:publish   # in compose-cast
//   ./gradlew --init-script <compose-cast>/scripts/local-maven.init.gradle.kts assembleDebug  # in the app
//
// The local repository is searched first. Default: <compose-cast>/build/maven-dry-run; override
// with -Dcomposecast.localMaven=/path/to/repo.
val localRepo: String = System.getProperty("composecast.localMaven")
    ?: File(initscript.sourceFile!!.parentFile.parentFile, "build/maven-dry-run").absolutePath

beforeSettings {
    pluginManagement.repositories.maven { url = uri(localRepo) }
    dependencyResolutionManagement.repositories.maven { url = uri(localRepo) }
}
