# 0035 — Three-tier Android mobile architecture: public SDK, shell, zero-code apps

- Status: Accepted (owner, 2026-11-27)
- Date: 2026-11-27
- Amends: [0017](0017-apps-in-own-repositories.md), [0018](0018-distribution-like-immich.md) (adds direct `sdk-android` consumption alongside zero-code `app-template` and `app-preview`)

## Context

Until now, `io.composecast:sdk-android` has been published on Reposilite ([ADR 0019](0019-self-hosted-maven-reposilite.md)),
but only as a low-level building block for `app-shell`: `ComposeCastClient`, `ScreenDocument`, and
the raw `ComposeCastScreen(screen: ScreenDocument)` composable. Everything needed to actually run
server-driven screens (fetching, loading/error/retry states, `426 Upgrade Required` handling, live
WebSocket lifecycle, back stack with state restoration, and `Action` routing) lived as `private`
code inside `app-shell/ComposeCastActivity.kt`.

Owner, 2026-11-27: keep `app-preview` and `app-template` (zero-code apps via `io.composecast.app`),
**and** make `sdk-android` a first-class published SDK that both `app-template`/`app-preview` (via
`app-shell`) and external Android developers can use directly when they want to embed compose-cast
screens or multi-screen flows inside their own Android app instead of using `app-template`.

## Decision

1. **Three-tier Android architecture** (strictly layered, zero duplication):
   - **Tier 1 — Public SDK (`io.composecast:sdk-android` + `io.composecast:protocol`):**
     usable directly by any Jetpack Compose Android app at three levels:
     - *Single screen:* `ComposeCastView(client, screenId, params, loading, error, onAction)` —
       fetches the screen, handles ETag cache and live WebSocket updates (`liveEveryMs`), and exposes
       typed `ComposeCastError` (`UpgradeRequired`, `Http`, `Network`) through composable slots.
     - *Multi-screen flow:* `ComposeCastFlow` + `ComposeCastNavigator`
       (`rememberComposeCastNavigator`) — manages a saveable back stack, `BackHandler`, default
       `Action.Navigate` / `Action.Reload` / `Action.OpenUrl` handling, and an `onAction: (Action) -> Boolean`
       interceptor so host apps can bridge server-driven actions to native screens or analytics.
     - *Low-level:* `ComposeCastClient` + `ComposeCastScreen(ScreenDocument)` — full manual control.
   - **Tier 2 — Standalone shell (`io.composecast:app-shell`):**
     a thin wrapper over Tier 1's public API providing `ComposeCastActivity`, `AndroidManifest`
     (`LAUNCHER`), resource-based configuration (`resValue` contract with `io.composecast.app`),
     Material 3 chrome (`Scaffold`, `TopAppBar`, `Snackbar`), and Preview mode (`ConnectScreen`,
     `PreviewLink`). `app-shell` must dogfood `sdk-android`'s public API — no duplicate fetching,
     back-stack, or live-screen logic.
   - **Tier 3 — Zero-code apps (`app-template`, `app-preview`, exported app repositories):**
     configured purely via `compose-cast.properties` and the `io.composecast.app` Gradle plugin.

2. **`sdk-android` stays Material 3-free.**
   `sdk-android` depends only on `compose.runtime`, `compose.foundation`, and `compose.ui` (no
   `compose.material3`), so host apps with their own design system (Material 2, Material 3, or
   custom UI) are not forced into Material 3. `ComposeCastView` and `ComposeCastFlow` accept
   composable slots for `loading` and `error` with minimal foundation-only defaults; Material 3
   chrome stays in `app-shell`.

3. **SDK production-readiness requirements:**
   - `explicitApi()` enabled on `protocol` and `sdk-android` (with KDoc on all public declarations).
   - `ComposeCastConfig` supports `headers: suspend () -> Map<String, String>` (applied to both
     REST and WebSocket requests) so host apps can attach auth tokens or context without exposing
     Ktor/OkHttp types in the public API.
   - `ComposeCastClient` implements `Closeable` and provides `rememberComposeCastClient`.
   - `consumer-rules.pro` shipped in `sdk-android` (and `protocol`) so host apps with R8 enabled
     build and run safely.
   - Remote Compose types remain strictly confined to `sdk-android` internals ([ADR 0002](0002-confine-restricted-remote-compose-api.md),
     rules R1–R3).

4. **Distribution stays on Reposilite ([ADR 0019](0019-self-hosted-maven-reposilite.md)) for now.**
   Moving or mirroring to Maven Central later remains an additive, reversible change.

## Consequences

- `app-shell` shrinks significantly and continuously exercises the exact public SDK API external
  developers use.
- External Android developers get a clean, documented, R8-safe SDK without adopting `app-template`
  or `io.composecast.app`.
- Every public API change in `sdk-android` and `protocol` is now a consumer-facing contract;
  `explicitApi()` prevents accidental leaks.
- A minimal SDK consumer sample (without `io.composecast.app` or `app-shell`) is verified in CI
  against the Maven dry-run repository, including release R8 shrinking.

## How to verify it still holds

- `./scripts/check-architecture.sh` passes R1–R3 (no RC types in public `sdk-android` API).
- `sdk-android/build.gradle.kts` has `explicitApi()` and does not depend on `material3`.
- `app-shell` uses `ComposeCastFlow` / `ComposeCastNavigator` from `sdk-android` and passes
  `./scripts/verify.sh` plus the Maestro flows on the emulator.
- `./scripts/verify-sdk-consumer.sh` builds the standalone SDK consumer sample (`assembleRelease`
  with R8) against the local Maven dry-run artifacts.
