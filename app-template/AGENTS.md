# app-template — AGENTS.md

Demo Android application. Shows how to integrate `sdk-android` into a real app: navigation with
back stack, loading/error states, action routing.

## Scope

This module only. For SDK details, read `sdk-android/AGENTS.md`.
Do NOT modify this app to prove SDK behavior — use `compat-tests` for that.

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
- `navigate:screen_id` action → push `screen_id` onto `backStack`
- System back → pop `backStack`
- `backStack` is `rememberSaveable` → survives rotation

## Action routing (in `ComposeCastApp`)

| Action | Handler |
|---|---|
| `navigate` | Push screen id onto back stack |
| `open_url` | `startActivity(Intent.ACTION_VIEW)` |
| `track` | No-op (analytics hook placeholder) |
| other | Show snackbar |

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
