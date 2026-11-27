# T-061 — Android SDK external consumer sample, CI verification & developer guide

Status: queued (after T-060) · Roadmap: H4 · Depends on: T-060 · Module: sdk-android, docs

## Goal

Prove that an external Android app can depend directly on `io.composecast:sdk-android` (without
`io.composecast.app` or `app-shell`), build a minified release APK with R8, and document the SDK
integration path clearly for Android developers.

## Why

[ADR 0035](../decisions/0035-android-sdk-public-api.md): we already verify the zero-code app path
with `distribution/android-app/` and `scripts/verify-app-repository.sh`. We need the exact same
end-to-end proof and documentation for the standalone SDK path.

## Design

1. **Minimal SDK consumer sample (`distribution/android-sdk-sample/`):**
   - Plain Android Gradle project (`com.android.application`, Kotlin, Compose) — **no**
     `io.composecast.app` plugin and **no** `app-shell`.
   - Declares Reposilite (`maven.jarka.club/releases`, overridable via `-PcomposecastRepo` for
     dry-run verification) and `implementation("io.composecast:sdk-android:<version>")`.
   - `MainActivity` demonstrates:
     - Configuring `ComposeCastClient` (including custom headers).
     - Embedding a single screen with `ComposeCastView` and a multi-screen flow with
       `ComposeCastFlow` + `onAction` interception.
   - `release` build type enables `isMinifyEnabled = true` so R8 shrinking + `consumer-rules.pro`
     are verified on every run.
2. **Verification script (`scripts/verify-sdk-consumer.sh`):**
   - Publishes `protocol` and `sdk-android` to `build/maven-dry-run` (same pattern as
     `scripts/verify-app-repository.sh`).
   - Copies `distribution/android-sdk-sample/` to a temp dir and runs `./gradlew assembleDebug assembleRelease`.
   - Hooked into `scripts/verify.sh` (or CI).
3. **Developer documentation:**
   - New `docs/guides/android-sdk.md`:
     - Adding the repository and `io.composecast:sdk-android` dependency.
     - Level 1: Single screen (`ComposeCastView`).
     - Level 2: Multi-screen flow (`ComposeCastFlow` + `ComposeCastNavigator` + bridging actions to
       native screens).
     - Level 3: Low-level (`ComposeCastClient` + `ComposeCastScreen`).
     - Custom headers / auth, live screens, dark mode, error handling (`ComposeCastError`), R8.
   - Update `README.md` to present all 3 Android integration paths: Preview app, Zero-code branded
     app (`app-template` / plugin), and Android SDK (`sdk-android`).

## Steps (files to touch)

1. `distribution/android-sdk-sample/`: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`,
   `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/.../MainActivity.kt`.
2. `scripts/verify-sdk-consumer.sh` (executable) + wire into `scripts/verify.sh` / `.github/workflows/ci.yml`.
3. `docs/guides/android-sdk.md`.
4. `README.md`, `AGENTS.md` (index link to the SDK guide).

## Acceptance criteria

- `./scripts/verify-sdk-consumer.sh` passes cleanly, including `assembleRelease` with
  `isMinifyEnabled = true` (proving R8 consumer rules work).
- `distribution/android-sdk-sample/` has no dependency on `app-shell` or `io.composecast.app`.
- `docs/guides/android-sdk.md` covers all 3 SDK usage levels with accurate, copy-pasteable Kotlin.
- `./scripts/verify.sh` is green.

## Verification

- `./scripts/verify-sdk-consumer.sh`
- `./scripts/verify.sh`

## Docs to update

- `docs/guides/android-sdk.md`
- `README.md`
- `docs/history.md`

## Out of scope

- Publishing to Maven Central (stays on Reposilite per ADR 0019 / ADR 0035).
