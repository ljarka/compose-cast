# T-052 — Android client cleanup (single OkHttp engine, timeouts, release logging)

Status: done · Roadmap: quality · Depends on: T-050 · Module: sdk-android, app-shell

## Goal

Simplify `sdk-android` to a single HTTP engine (`OkHttp`) with explicit timeouts, and avoid
hardcoding verbose HTTP logging in release builds of `app-shell`.

## Why

- `sdk-android` currently depends on both `ktor-client-android` (for REST) and `ktor-client-okhttp`
  (for WebSockets), keeping two separate `HttpClient` instances and adding unnecessary dependencies.
- `ComposeCastClient` installs no `HttpTimeout`, so stalled connections can hang indefinitely.
- `ComposeCastActivity` hardcodes `logEnabled = true` even in release builds.

## Design

- Use a single `HttpClient(OkHttp)` in `ComposeCastClient` with `WebSockets` and `HttpTimeout`
  (sensible defaults, configurable via `ComposeCastConfig`).
- Drop `ktor-client-android` from `sdk-android/build.gradle.kts` (and `libs.versions.toml` if
  unused elsewhere).
- Set `logEnabled` in `ComposeCastActivity` from `applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0`
  (works without requiring `buildFeatures.buildConfig = true` in `app-shell`).

## Steps (files to touch)

1. `sdk-android/build.gradle.kts`, `gradle/libs.versions.toml`: remove `ktor-client-android`.
2. `sdk-android/.../ComposeCastClient.kt` & `ComposeCastConfig.kt`: single OkHttp client + timeouts.
3. `app-shell/.../ComposeCastActivity.kt`: enable client logs only in debuggable builds.
4. Update `ComposeCastClientTest`.

## Acceptance criteria

- `sdk-android` uses only `OkHttp` and configures connect/request/socket timeouts.
- `ComposeCastActivity` disables HTTP logging in non-debuggable builds.
- `./scripts/verify.sh` is green.

## Verification

`./scripts/verify.sh`

## Docs to update

`sdk-android/AGENTS.md`, `app-shell/AGENTS.md`, `docs/history.md`.

## Out of scope

Offline disk cache (T-028).
