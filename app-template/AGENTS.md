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
| `maestro/smoke.yaml` | Maestro UI flow: launch, tap "Get started", back (`-e APP_ID=<id>` for other configs) |
| `compose-cast.properties` | Per-app configuration (T-011): name, `applicationId`, server URL, frame colors, icon |
| `build.gradle.kts` | Reads the properties (`ComposeCastAppConfig`), icon task (`GenerateLauncherIconTask`), release https check |
| `res/mipmap-anydpi/ic_launcher.xml`, `res/drawable/ic_launcher_foreground.xml` | Default adaptive icon (cast glyph on `app.frame.primaryColor`) |

## Usage

The app connects to `app.serverUrl` from `compose-cast.properties` (demo: `http://10.0.2.2:8080`,
the emulator alias for host `localhost`).

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

## Configuration (T-011)

One file, `compose-cast.properties` (or `-Pcomposecast.appConfig=<path>`, relative to the root
project), makes a new app without code changes:

| Key | Becomes |
|---|---|
| `app.name` | `resValue string app_name` (launcher label) |
| `app.applicationId` | `applicationId` (`namespace` stays `io.composecast.demo`) |
| `app.serverUrl` | `BuildConfig.SERVER_URL`; release variants: `check<Variant>ComposeCastConfig` before `pre<Variant>Build` fails unless `https://` |
| `app.frame.primaryColor` | `BuildConfig.FRAME_PRIMARY_COLOR` → top app bar, status bar icon style; `@color/composecast_frame_primary` (default icon background) |
| `app.frame.backgroundColor` | `BuildConfig.FRAME_BACKGROUND_COLOR` → loading/error background; `@color/composecast_frame_background` → window/splash |
| `app.icon` (optional) | `generateComposeCastLauncherIcon` → `@mipmap/ic_launcher_custom` (foreground fit into the 72dp safe zone, PNG per density); manifest placeholder `appIcon` |
| `app.icon.backgroundColor` (optional) | Custom icon background, default `app.frame.primaryColor` |

Decisions:
- Validation happens at configuration time (missing key → `Missing key 'app.name' in …`), except
  the https rule, which only runs for release variants so debug builds keep a local http server.
- The config code at the bottom of `build.gradle.kts` uses no `app-template` paths: T-024 moves it
  unchanged into the `io.composecast.app` plugin.
- Content colors are not configured here (server `BrandConfig`); the RC document paints over the
  frame background once loaded.

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
