# T-059 — High-level Android SDK Composables (`ComposeCastView`, `ComposeCastNavigator`, `ComposeCastFlow`)

Status: ready · Roadmap: H2 · Depends on: T-058 · Module: sdk-android

## Goal

Add drop-in Jetpack Compose APIs to `io.composecast:sdk-android` so external Android apps (and
`app-shell` in T-060) can embed a single server-driven screen (`ComposeCastView`) or a complete
multi-screen flow (`ComposeCastFlow` + `ComposeCastNavigator`) without re-implementing fetching,
live-screen WebSocket lifecycle, back stack, or action routing.

## Why

[ADR 0035](../decisions/0035-android-sdk-public-api.md): `sdk-android` stays Material 3-free
(`compose.runtime`, `compose.foundation`, `compose.ui` only) and provides slot-based high-level
Composables that `app-shell` dogfoods.

## Design

1. **Single-screen composable (`ComposeCastView.kt`):**
   - `public sealed interface ComposeCastUiState`: `Loading`, `Ready(val screen: ScreenDocument)`,
     `Error(val error: ComposeCastError)`.
   - `@Composable public fun ComposeCastView(...)`:
     - Parameters: `client: ComposeCastClient`, `screenId: String`, `params: Map<String, String> = emptyMap()`,
       `modifier: Modifier = Modifier`, `reloadKey: Any = 0`, `darkTheme: Boolean = isSystemInDarkTheme()`,
       `onAction: (Action) -> Unit = {}`, `onStateChanged: ((ComposeCastUiState) -> Unit)? = null`,
       `loading: @Composable () -> Unit = { DefaultLoading() }`,
       `error: @Composable (ComposeCastError, retry: () -> Unit) -> Unit = { err, retry -> DefaultError(err, retry) }`.
     - Internally fetches `client.getScreen(screenId, params)`, maps exceptions to `ComposeCastError`,
       wires `client.liveScreen(screenId, params)` when `screen.liveEveryMs != null` (guarded so
       stale frames during navigation never connect to the wrong screen), and delegates rendering
       to `ComposeCastScreen`.
     - Default loading/error UI uses only `compose.foundation` / `compose.ui` (no Material 3).
2. **Navigation state (`ComposeCastNavigator.kt`):**
   - `public data class ScreenEntry(val screen: String, val params: Map<String, String> = emptyMap())`
     (or reuse `Action.Navigate` directly — decide and document; `Action.Navigate` already serializes
     cleanly via `ActionCodec`!).
   - `public class ComposeCastNavigator`:
     - `val backStack: List<Action.Navigate>` (Compose state)
     - `val current: Action.Navigate?`
     - `val canGoBack: Boolean`
     - `val reloadCount: Int`
     - `fun navigate(screen: String, params: Map<String, String> = emptyMap())`
     - `fun navigate(action: Action.Navigate)`
     - `fun replaceRoot(screen: String, params: Map<String, String> = emptyMap())`
     - `fun pop(): Boolean`
     - `fun reload()`
   - `@Composable public fun rememberComposeCastNavigator(startScreen: String? = "main", startParams: Map<String, String> = emptyMap()): ComposeCastNavigator`
     backed by `rememberSaveable` (using `ActionCodec` wire strings so the back stack survives
     configuration changes).
3. **Multi-screen flow (`ComposeCastFlow.kt`):**
   - `@Composable public fun ComposeCastFlow(...)`:
     - Parameters: `client: ComposeCastClient`, `navigator: ComposeCastNavigator`,
       `modifier: Modifier = Modifier`, `handleSystemBack: Boolean = true`,
       `darkTheme: Boolean = isSystemInDarkTheme()`,
       `onAction: (Action) -> Boolean = { false }` (returns `true` if handled by host; `false`
       falls through to default SDK behavior: `Navigate` → `navigator.navigate(action)`, `Reload` →
       `navigator.reload()`, `OpenUrl` → `onOpenUrl(action.url)`),
       `onOpenUrl: (String) -> Unit = defaultOpenUrl(context)`,
       `onStateChanged: ((ComposeCastUiState) -> Unit)? = null`,
       `loading: @Composable () -> Unit = ...`,
       `error: @Composable (ComposeCastError, retry: () -> Unit) -> Unit = ...`.
     - Uses `BackHandler(enabled = handleSystemBack && navigator.canGoBack) { navigator.pop() }`
       (requires `activity-compose` or `androidx.activity.compose.BackHandler` — verify dependency;
       `activity-compose` is tiny and standard, or keep `handleSystemBack` clean).

## Steps (files to touch)

1. `sdk-android/build.gradle.kts`: add `activity-compose` (only if needed for `BackHandler`), keep
   Material 3 out.
2. `sdk-android/src/main/kotlin/io/composecast/android/ComposeCastView.kt`: `ComposeCastUiState`
   and `ComposeCastView`.
3. `sdk-android/src/main/kotlin/io/composecast/android/ComposeCastNavigator.kt`: `ComposeCastNavigator`
   and `rememberComposeCastNavigator`.
4. `sdk-android/src/main/kotlin/io/composecast/android/ComposeCastFlow.kt`: `ComposeCastFlow` and
   default action handling.
5. `sdk-android/src/test/kotlin/io/composecast/android/ComposeCastNavigatorTest.kt`: unit tests for
   push, pop, replaceRoot, reload, and `ActionCodec` saver round-trip.

## Acceptance criteria

- `sdk-android` has zero Material 3 dependency.
- `ComposeCastView` renders a fetched screen, auto-subscribes to live updates, and invokes
  `loading` / `error` slots properly.
- `ComposeCastFlow` handles `Action.Navigate` / `Action.Reload` / `Action.OpenUrl` by default and
  lets `onAction` intercept any action (returning `true` suppresses default behavior).
- `explicitApi()` and `./scripts/verify.sh` are green.

## Verification

- `./gradlew :sdk-android:testDebugUnitTest :sdk-android:lintDebug`
- `./scripts/verify.sh`

## Docs to update

- `sdk-android/AGENTS.md`
- `docs/history.md`

## Out of scope

- Migrating `app-shell` to use `ComposeCastFlow` (T-060).
- External consumer sample app and guide (T-061).
