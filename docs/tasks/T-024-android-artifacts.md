# T-024 — Android artifacts on Maven Central + `io.composecast.app` Gradle plugin

Status: queued (after T-023) · Roadmap: Etap 6 · Depends on: T-011 (`compose-cast.properties`), owner Maven Central setup

## Goal
An app repository builds a branded Android app with **no Kotlin sources** — only Gradle files,
`compose-cast.properties` and an icon ([ADR 0018](../decisions/0018-distribution-like-immich.md)):

```kotlin
// settings.gradle.kts: pluginManagement/dependencyResolution → google(), mavenCentral(), gradlePluginPortal()
// build.gradle.kts
plugins { id("io.composecast.app") version "0.1.0" }
```

## Design
1. New Android library module **`app-shell`**: move `MainActivity`, navigation/back stack, error UI
   from `app-template` (code package unchanged or `io.composecast.shell`; decide when moving). It
   reads server URL and frame colors from resources/`BuildConfig`-like values the plugin injects
   (a library has no app `BuildConfig`: use `resValue`/manifest placeholders → `<meta-data>` read at
   runtime).
2. **Gradle plugin `io.composecast.app`** (included build `gradle-plugins/`, Kotlin, `java-gradle-plugin`):
   applies `com.android.application`, reads `compose-cast.properties` (T-011 logic moves here),
   sets `applicationId`, `app_name`, placeholders, icon generation, adds
   `io.composecast:app-shell:<plugin version>`; fails on missing keys and on `http://` for release.
3. `app-template` becomes a consumer of the plugin through the included build: it then has the same
   files as an app repository (proves the path). Its Kotlin sources move to `app-shell`.
4. Publishing: `com.vanniktech.maven.publish` for `protocol`, `sdk-android`, `app-shell` and the
   plugin (with plugin marker), signed, to the Central Portal; group = owner's namespace (see below),
   version = the release tag. Added to `.github/workflows/release.yml` from T-023.
5. `docs/playbooks/new-app-repository.md`: the whole path (server from T-023 + Android from here),
   with the four Android files spelled out.

## Owner actions (cannot be done by an agent)
- Central Portal account (https://central.sonatype.com), namespace: `io.github.ljarka` (verified via
  GitHub) or a custom domain group (DNS TXT). Decide the group before step 4.
- GPG key; repository secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
  `SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_PASSWORD`.

## Acceptance criteria
- `./gradlew publishToMavenLocal` + a scratch app repository using `mavenLocal()` builds and runs on
  the emulator against the Docker server with no `.kt` files.
- `app-template` builds through the plugin; screenshots unchanged (`verify.sh` green).
- Release workflow publishes all four artifacts on a tag (dry run: `publishToMavenLocal` in CI).

## Verification
`./scripts/verify.sh`; [verify-on-emulator.md](../playbooks/verify-on-emulator.md) with the scratch
app repository.

## Docs to update
`app-template/AGENTS.md`, new `app-shell/AGENTS.md`, `sdk-android/AGENTS.md`, README (Etap 6/7/11),
`docs/history.md`, T-010 (export = app repository files).

## Out of scope
iOS (SwiftPM, Etap 3/7), Play upload, admin export (T-010).
