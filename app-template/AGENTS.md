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
      ├── backStack: List<Action.Navigate> (rememberSaveable, BackStackSaver) + BackHandler; entry = last
      ├── produceState(entry, reloadKey) → ComposeCastClient.getScreen(entry.screen, entry.params)
      ├── loading  → CircularProgressIndicator
      ├── error    → ComposeCastHttpException: "Server error <status>"; else server URL hint; Retry (reloadKey++)
      └── success  → ComposeCastScreen(screen, onAction: (Action) -> Unit)   (Remote Compose player)
                          └── Navigate: pushes the action (screen + params) onto backStack
                              OpenUrl:  Intent.ACTION_VIEW
                              Reload:   reloadKey++ → re-fetch (If-None-Match → usually 304)
                              Track:    analytics hook (no-op in demo)
                              Custom:   Snackbar
```

## Key files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Entry point; creates `ComposeCastClient`; hosts `ComposeCastApp` composable |
| `ComposeCastApp` (in MainActivity) | Back stack (`rememberSaveable`), loading/error states, action routing |

## Usage

The app connects to the URL in `compose-cast.properties` (`app.serverUrl`; default
`http://10.0.2.2:8080` = emulator alias for host `localhost`). For a real device or remote
server change that value; cleartext HTTP is allowed only in debug builds and only for local
addresses.

## Back stack

Navigation is manual:
- `Action.Navigate(screen, params)` → push the action itself onto `backStack` (entries are
  `Action.Navigate`; params become the query of `GET /screens/{screen}`)
- System back → pop `backStack`
- `backStack` is `rememberSaveable` with `BackStackSaver`: entries saved as their `ActionCodec`
  wire strings → survives rotation (verified on the emulator, params included)

## Action routing (in `ComposeCastApp`)

Exhaustive `when (action)` over `io.composecast.protocol.Action` (a new subtype is a compile error):

| Action | Handler |
|---|---|
| `Navigate` | Push the entry (screen + params) onto the back stack |
| `OpenUrl` | `startActivity(Intent.ACTION_VIEW)`; no app for the URL → snackbar (not a crash) |
| `Reload` | `reloadKey++` → `produceState` re-fetches the current screen |
| `Track` | No-op (analytics hook placeholder) |
| `Custom` | Show snackbar |

Unknown actions never reach the app: `sdk-android` logs and drops them.

## Configuration

All app-specific values live in `compose-cast.properties` (project root of this module).
`build.gradle.kts` reads it at build time and exposes them as `BuildConfig` fields; a missing
key is a build error that names the key.

```properties
app.name=ComposeCast Demo
app.applicationId=io.composecast.demo
app.serverUrl=http://10.0.2.2:8080
# native frame colors (top app bar, status bar) before the first document arrives
app.frame.primaryColor=#6200EE
app.frame.backgroundColor=#FAFAFA
# optional PNG/JPG path for the launcher icon; empty = keep the default icon
app.icon=
```

`MainActivity` reads `BuildConfig.SERVER_URL`, `BuildConfig.FRAME_PRIMARY_COLOR`, and
`BuildConfig.FRAME_BACKGROUND_COLOR`; it never hard-codes these values.
Release builds reject `http://` server URLs with a clear Gradle error.

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
