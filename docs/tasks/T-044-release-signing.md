# T-044 — Release signing configured by the app repository

Status: queued (after T-042) · Roadmap: Etap 6 · Depends on: — · Module: gradle-plugins

## Goal

`./gradlew assembleRelease` in an app repository produces a **signed** APK/AAB without Kotlin.
Found by the weather app (T-025): the release build is `weather-release-unsigned.apk`.

## Design

- `compose-cast.properties`: `app.signing.storeFile` (path relative to the file, the keystore itself
  gitignored) and `app.signing.keyAlias`. Passwords never in the file: env
  `COMPOSECAST_SIGNING_STORE_PASSWORD` / `COMPOSECAST_SIGNING_KEY_PASSWORD` (CI secrets) or Gradle
  properties in `~/.gradle/gradle.properties`.
- All keys optional; without them the build stays unsigned (today's behavior). A partial setup
  fails with the missing key's name (like the other `app.*` keys).
- Rule 8 (no keystores or secrets in git): the template `.gitignore` gets `*.jks`, `*.keystore`.

## Acceptance criteria

- Plugin test (`gradle-plugins` functional test): signing config applied, missing password → clear error.
- `verify-app-repository.sh` unchanged (no signing); manual: weather app signs with a throwaway keystore.

## Docs to update

`gradle-plugins/AGENTS.md` (keys), `new-app-repository.md`, `distribution/android-app/compose-cast.properties`
(commented keys), history.

## Out of scope

Play Store upload (T-025 out of scope), app bundles per ABI.
