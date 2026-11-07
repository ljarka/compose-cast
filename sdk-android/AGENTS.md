# sdk-android — AGENTS.md

Android SDK for compose-cast. Fetches server-driven screens and renders them with Jetpack Compose.

## Public API

| Class | Purpose |
|---|---|
| `ComposeCastConfig` | Server URL, player version, logging flag |
| `ComposeCastClient` | Ktor client: `getScreen`, `listScreens`, `getBrand` |
| `ComposeCastScreen` | Composable that renders a `ScreenModel` |

## Usage Pattern (app-template)

```kotlin
val client = ComposeCastClient(ComposeCastConfig(serverUrl = "http://10.0.2.2:8080"))

val screen by produceState<ScreenModel?>(null) {
    value = client.getScreen("main")
}

screen?.let {
    ComposeCastScreen(screen = it, onAction = { action, payload ->
        // handle navigate / open_url / track / custom
    })
}
```

## Action Routing

Button actions follow the `type:payload` convention from `Actions.kt`:
- `navigate:screen_id` — navigate to another screen
- `open_url:https://...` — open external URL
- `track:event_name` — analytics event
- `custom:any_payload` — host-defined behavior

## Decisions

- Ktor Android engine (consistent with server-side Ktor)
- `kotlinx.serialization` for JSON (consistent with protocol module)
- Image rendering: placeholder text only — host app provides image loading (e.g. Coil)
- `RemoteComposePlayer` integration deferred until server emits RC binary format
