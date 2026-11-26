# T-050 — Close CI verification gaps (`app-templates` and `sdk-android` tests)

Status: done · Roadmap: quality · Depends on: — · Module: sdk-android, app-templates

## Goal

Ensure every module's unit tests actually run in `./scripts/verify.sh` and CI, and add unit test
coverage for `ComposeCastClient` in `sdk-android`.

## Why

- `scripts/verify.sh` lists JVM modules explicitly and omits `:app-templates:build`, so
  `:app-templates:test` is silently skipped in local verification and CI.
- `sdk-android` currently has 0 unit tests and `:sdk-android:testDebugUnitTest` is not run by
  `verify.sh`, leaving `ComposeCastClient`'s LRU ETag cache (`304`), `426 Upgrade Required`
  handling, title decoding, and WebSocket backoff/refuse logic untested.

## Design

- Add `:app-templates:build` to the JVM step and `:sdk-android:testDebugUnitTest` to the Android
  step in `scripts/verify.sh`.
- Allow injecting a custom `HttpClientEngine` (internal constructor) into `ComposeCastClient` so
  unit tests can run fast on the JVM with `ktor-client-mock` (no network, no Robolectric).
- Test `getScreen` (200 + ETag caching, `304 Not Modified` cache hit, URL-decoded title, `426`
  mapped to `ComposeCastUpgradeRequiredException`, non-2xx mapped to `ComposeCastHttpException`,
  and LRU eviction), `listScreens`, and `getBrand`.

## Steps (files to touch)

1. `gradle/libs.versions.toml` and `sdk-android/build.gradle.kts`: add `ktor-client-mock` for tests.
2. `sdk-android/src/main/kotlin/io/composecast/android/ComposeCastClient.kt`: internal constructor
   accepting `HttpClientEngine` (and `ScreenDocument` decoder hook if needed for pure JVM tests).
3. `sdk-android/src/test/kotlin/io/composecast/android/ComposeCastClientTest.kt`: unit tests.
4. `scripts/verify.sh`: add `:app-templates:build` and `:sdk-android:testDebugUnitTest`.

## Acceptance criteria

- `./scripts/verify.sh` runs both `:app-templates:test` and `:sdk-android:testDebugUnitTest`.
- `ComposeCastClientTest` covers 200, 304 ETag reuse, 426, 404/500, title decoding, `listScreens`,
  and `getBrand`.
- `./scripts/verify.sh` is green.

## Verification

`./scripts/verify.sh`

## Docs to update

`sdk-android/AGENTS.md`, `docs/history.md`, `docs/tasks/README.md` (`./scripts/gen-status.sh`).

## Out of scope

Unifying HTTP engines in `sdk-android` (T-052).
