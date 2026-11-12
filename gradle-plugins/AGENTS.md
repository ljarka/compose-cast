# gradle-plugins — AGENTS.md

Included build (not a module of the main build) with the **`io.composecast.app`** Gradle plugin
(T-011 logic, T-024): a project with only `compose-cast.properties` (and an optional icon) becomes a
branded Android app. Included by the root `settings.gradle.kts` (`pluginManagement.includeBuild`,
skipped with `-Pcomposecast.serverOnly=true`); published as `io.composecast:gradle-plugins` + the
plugin marker `io.composecast.app:io.composecast.app.gradle.plugin`.

## Files

| File | Purpose |
|---|---|
| `ComposeCastAppPlugin.kt` | Applies `com.android.application`; reads the config; sets namespace (default = applicationId), SDK levels, applicationId, version, `resValue`s; placeholder main manifest if the project has none; icon + debug network config tasks; release https check; adds the shell |
| `ComposeCastAppConfig.kt` | Parses/validates `compose-cast.properties` (missing/invalid key → `GradleException` naming it) |
| `GenerateLauncherIconTask.kt` | `app.icon` → `mipmap-anydpi/ic_launcher.xml` (overrides the shell's) + foreground PNG per density, fit into the 72dp safe zone |
| `GenerateDebugNetworkConfigTask.kt` | Debug variants: `xml/composecast_network_security_config.xml` allowing cleartext to local dev hosts |
| `build.gradle.kts` | `java-gradle-plugin`, `implementation(libs.android.gradle)` (AGP comes with the plugin), version resource, publishing (mirrors the root block) |

## `compose-cast.properties`

| Key | Becomes |
|---|---|
| `app.name` | `resValue string app_name` |
| `app.applicationId` | `applicationId` (and `namespace` unless the project sets one) |
| `app.versionCode`, `app.versionName` (optional) | default `1`, `1.0` |
| `app.serverUrl` | `resValue string composecast_server_url`; non-debug variants: `check<Variant>ComposeCastConfig` before `pre<Variant>Build` fails unless `https://` |
| `app.frame.primaryColor`, `app.frame.backgroundColor` | `resValue color composecast_frame_primary` / `composecast_frame_background` |
| `app.icon`, `app.icon.backgroundColor` (optional) | generated launcher icon; background default = frame primary |

File: `compose-cast.properties` in the project dir, or `-Pcomposecast.appConfig=<path>` (relative
to the root project). The resource names are the contract with `app-shell` (see its AGENTS.md).

## Decisions

- **Shell dependency:** `project(":app-shell")` when the build has that project (this repository),
  else `io.composecast:app-shell:<plugin version>` (version from a generated `plugin.properties`).
  Plugin and shell are always released together in one version.
- **Kotlin:** compiled with the catalog's Kotlin but `apiVersion`/`languageVersion` 2.0, because
  the plugin runs on Gradle's embedded (older) Kotlin stdlib. JVM 17 bytecode (AGP 9 minimum).
- **AGP on the classpath twice in this repository** (root `apply false` + the plugin's dependency):
  same version from the catalog, works; keep them equal.
- Validation at configuration time, except the https rule (only when a non-debug variant builds),
  so debug builds keep working with a local http server.

## Verify

```bash
./gradlew :app-template:assembleDebug                 # through the plugin (included build)
./scripts/verify-app-repository.sh [--install]        # publish dry run + distribution/android-app build
```
