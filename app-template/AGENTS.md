# app-template — AGENTS.md

Demo Android application. Shows how to integrate `sdk-android` into a real app: navigation with
back stack, loading/error states, action routing.

## Scope

This module only. For SDK details, read `sdk-android/AGENTS.md`.
Do NOT modify this app to prove SDK behavior — use `compat-tests` for that.

## Architecture

```
MainActivity
 └── ComposeCastApp (Composable)
      ├── backStack (rememberSaveable) + BackHandler; screenId = backStack.last()
      ├── produceState(screenId, reloadKey) → ComposeCastClient.getScreen(screenId) → ScreenDocument
      ├── loading  → CircularProgressIndicator
      ├── error    → error message with server URL hint
      └── success  → ComposeCastScreen(screen, onAction: (Action) -> Unit)   (Remote Compose player)
                          └── Navigate: pushes action.screen onto backStack (params: T-003)
                              OpenUrl:  Intent.ACTION_VIEW
                              Reload:   reloadKey++ → re-fetch current screen
                              Track:    analytics hook (no-op in demo)
                              Custom:   Snackbar
```

## Key files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Entry point; creates `ComposeCastClient`; hosts `ComposeCastApp` composable |
| `ComposeCastApp` (in MainActivity) | Back stack (`rememberSaveable`), loading/error states, action routing |

## Usage

The app connects to `http://10.0.2.2:8080` (emulator alias for host `localhost`).
For a real device or remote server, change `SERVER_URL` in `MainActivity`.

## Back stack

Navigation is manual:
- `Action.Navigate(screen)` → push `screen` onto `backStack` (params ignored until T-003)
- System back → pop `backStack`
- `backStack` is `rememberSaveable` → survives rotation

## Action routing (in `ComposeCastApp`)

Exhaustive `when (action)` over `io.composecast.protocol.Action` (a new subtype is a compile error):

| Action | Handler |
|---|---|
| `Navigate` | Push screen id onto back stack |
| `OpenUrl` | `startActivity(Intent.ACTION_VIEW)` |
| `Reload` | `reloadKey++` → `produceState` re-fetches the current screen |
| `Track` | No-op (analytics hook placeholder) |
| `Custom` | Show snackbar |

Unknown actions never reach the app: `sdk-android` logs and drops them.

## Configuration

```kotlin
companion object {
    const val SERVER_URL = "http://10.0.2.2:8080"  // change for real device
}
```

## Build

```bash
./gradlew :app-template:assembleDebug
./gradlew :app-template:installDebug   # installs to connected device/emulator
```

APK: `app-template/build/outputs/apk/debug/app-template-debug.apk`

## Constraints

- `minSdk = 26` (required by `remote-player-view` — [ADR 0005](../docs/decisions/0005-player-view-wrapper.md))
- No `org.jetbrains.kotlin.android` plugin (AGP 9.5.1 built-in Kotlin)
- No RC imports — only `sdk-android` public API
