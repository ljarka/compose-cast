# app-template — AGENTS.md

Demo Android application for compose-cast. Shows a full end-to-end example:
server → `ComposeCastClient` → `ComposeCastScreen` → native Compose UI.

## Run

1. Start the server:
   ```bash
   ./gradlew :server-app:run
   ```
2. Open `app-template` in Android Studio, run on emulator or device.
3. The app connects to `http://10.0.2.2:8080` (emulator alias for host localhost).
   For a real device, change `SERVER_URL` in `MainActivity.kt`.

## Architecture

```
MainActivity
 └── ComposeCastApp (Composable)
      ├── backStack (rememberSaveable) + BackHandler; screenId = backStack.last()
      ├── produceState → ComposeCastClient.getScreen(screenId) → ScreenDocument
      ├── loading  → CircularProgressIndicator
      ├── error    → error message with server URL hint
      └── success  → ComposeCastScreen(screen, onAction)   (Remote Compose player)
                          └── navigate: pushes onto backStack → re-fetches
                              open_url: fires Intent.ACTION_VIEW
                              track: analytics hook (no-op in demo)
                              custom: Snackbar
```

## Key Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Activity; holds `ComposeCastClient`; wires up `ComposeCastApp` |
| `AndroidManifest.xml` | INTERNET permission, launcher activity |
| `src/debug/res/xml/network_security_config.xml` | Debug-only cleartext HTTP to 10.0.2.2 / localhost |

## Decisions

- `SERVER_URL = "http://10.0.2.2:8080"` — emulator default; change for real device
- `produceState` keyed on `screenId` — re-fetches on every navigation
- `TopAppBar` title comes from `ScreenModel.title` (falls back to screen id while loading)
- `android:Theme.Material.Light.NoActionBar` theme — prevents duplicate ActionBar with TopAppBar
- System back pops the screen back stack; the app exits only from the first screen
