# T-058 — Android SDK core hardening (`explicitApi()`, `ComposeCastError`, custom headers, R8 rules)

Status: ready · Roadmap: H1 · Depends on: — · Module: protocol, sdk-android

## Goal

Make `io.composecast:sdk-android` (and its `api` dependency `protocol`) safe, extensible, and
self-contained for external Android developers before adding high-level Composables (T-059).

## Why

[ADR 0035](../decisions/0035-android-sdk-public-api.md) turns `sdk-android` into a first-class
published SDK consumed by both `app-shell` and external Android apps. Today:
- Neither `sdk-android` nor `protocol` enforces `explicitApi()`, so internal helpers can leak.
- `ComposeCastConfig` has no way for host apps to pass auth or context HTTP headers.
- `ComposeCastClient` does not implement `Closeable` and has no Compose lifecycle helper.
- Errors are exposed only as raw exceptions (`ComposeCastHttpException`, `IOException`, etc.).
- No `consumer-rules.pro` exists for host apps with R8/minify enabled.

## Design

1. **`explicitApi()`** on `sdk-android` and `protocol` (aligns with T-055; T-055 will still add
   `sdk-server` `explicitApi()` and `detekt`). Audit every public declaration and mark internal
   helpers `internal`.
2. **`ComposeCastConfig`:**
   - Add `val headers: suspend () -> Map<String, String> = { emptyMap() }` — applied to `getScreen`,
     `liveScreen`, `listScreens`, and `getBrand` (do not overwrite reserved `Headers.PLAYER_VERSION`,
     `Headers.RC_API_LEVEL`, `Headers.RC_PROFILES`, or `If-None-Match`).
   - Add `val cacheSize: Int = 32` (validated `>= 0`).
   - Default `playerVersion` cleanly (keep backwards-compatible default `"1.0"` or SDK version).
3. **`ComposeCastClient`:**
   - Implement `java.io.Closeable` (delegates to existing `close()`).
   - Add `@Composable public fun rememberComposeCastClient(config: ComposeCastConfig): ComposeCastClient`
     with `DisposableEffect` cleanup (documenting singleton/DI as the recommended pattern for
     multi-screen apps).
4. **`ComposeCastError`:**
   - `public sealed interface ComposeCastError` with:
     - `data class UpgradeRequired(val minApiLevel: Int, val message: String) : ComposeCastError`
     - `data class Http(val statusCode: Int, val message: String) : ComposeCastError`
     - `data class Network(val cause: Throwable) : ComposeCastError`
   - Helper `Throwable.toComposeCastError(): ComposeCastError` (internal or public) mapping
     `ComposeCastUpgradeRequiredException`, `ComposeCastHttpException`, and other `Throwable`s.
5. **R8 / ProGuard consumer rules:**
   - Add `sdk-android/consumer-rules.pro` and register it via `consumerProguardFiles("consumer-rules.pro")`
     in `sdk-android/build.gradle.kts`, keeping `io.composecast.protocol.**` kotlinx.serialization
     serializers and Remote Compose player classes needed at runtime.
6. **KDoc:** clear KDoc on every public class, property, and function in `sdk-android`.

## Steps (files to touch)

1. `protocol/build.gradle.kts` + `protocol/src/main/kotlin/...`: enable `explicitApi()` and add
   explicit visibility modifiers.
2. `sdk-android/build.gradle.kts`: enable `explicitApi()`, wire `consumerProguardFiles("consumer-rules.pro")`.
3. `sdk-android/consumer-rules.pro`: add R8 keep rules.
4. `sdk-android/src/main/kotlin/io/composecast/android/`:
   - Update `ComposeCastConfig.kt` (`headers`, `cacheSize`, KDoc).
   - Update `ComposeCastClient.kt` (`Closeable`, apply `headers` on REST + WS, `rememberComposeCastClient`, KDoc).
   - Add `ComposeCastError.kt` (`ComposeCastError` sealed hierarchy).
   - Audit `ScreenDocument.kt` and `ComposeCastScreen.kt` for explicit visibility + KDoc.
5. `sdk-android/src/test/kotlin/io/composecast/android/ComposeCastClientTest.kt`: add tests for
   custom headers (including precedence of protocol headers), `cacheSize`, `Closeable`, and
   `ComposeCastError` mapping.

## Acceptance criteria

- `protocol` and `sdk-android` compile cleanly with `explicitApi()`.
- `ComposeCastConfig.headers` are attached to REST and WebSocket requests without overriding
  protocol negotiation headers.
- `ComposeCastError` classifies 426, non-2xx HTTP, and network errors accurately.
- `sdk-android` packages `consumer-rules.pro` into its AAR (`proguard.txt`).
- `./scripts/verify.sh` is green.

## Verification

- `./gradlew :protocol:test :sdk-android:testDebugUnitTest :sdk-android:lintDebug`
- `./scripts/verify.sh`

## Docs to update

- `sdk-android/AGENTS.md`
- `docs/history.md`

## Out of scope

- High-level Composables `ComposeCastView` / `ComposeCastFlow` (T-059).
- Refactoring `app-shell` (T-060).
- `detekt` setup (remains in T-055).
