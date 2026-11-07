# sdk-android — AGENTS.md

Android SDK for compose-cast. Downloads Remote Compose documents and plays them with the
AndroidX Remote Compose player (`remote-player-view`).

## Public API

| Class | Purpose |
|---|---|
| `ComposeCastConfig` | Server URL, player version, logging flag |
| `ComposeCastClient` | Ktor client: `getScreen` (→ `ScreenDocument`), `listScreens`, `getBrand` |
| `ScreenDocument` | Decoded RC document + `id`, `title` (from `X-Composecast-Screen-Title`), `sizeBytes` |
| `ComposeCastScreen` | Composable: `AndroidView` hosting `RemoteComposePlayer`; routes host actions |

## Usage Pattern (app-template)

```kotlin
val client = ComposeCastClient(ComposeCastConfig(serverUrl = "http://10.0.2.2:8080"))

val screen by produceState<ScreenDocument?>(null) { value = client.getScreen("main") }

screen?.let {
    ComposeCastScreen(screen = it, modifier = Modifier.fillMaxSize()) { action, payload ->
        // "navigate:onboarding" arrives as ("navigate", "onboarding")
    }
}
```

## Action Routing

Buttons emit RC host actions named `type:payload` (constants in `protocol/Actions.kt`):
`navigate:screen_id`, `open_url:https://...`, `track:event_name`, `custom:any_payload`.
`ComposeCastScreen` splits on the first `:`.

## Decisions

- Player: `remote-player-view` wrapped in our own `AndroidView` (~30 lines), not
  `remote-player-compose` (pulls Compose 1.11 → would force compileSdk 36 / newer AGP).
- RC player API is `@RestrictTo(LIBRARY_GROUP)` in alpha20; keep it inside this module.
- Document decoding (`RemoteDocument(bytes)`) runs on `Dispatchers.Default`, not the main thread.
- `expectSuccess = true`: unknown screen → `ClientRequestException` (404), not a decode error.
- Images are embedded by the server; the player never fetches URLs.
- `minSdk = 26` (required by `remote-player-*`).
