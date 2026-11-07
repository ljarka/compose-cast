# sdk-android — AGENTS.md

Android SDK for compose-cast. Downloads Remote Compose documents and plays them with the
AndroidX Remote Compose player (`remote-player-view`).

## Public API

| Class | Purpose |
|---|---|
| `ComposeCastConfig` | Server URL, player version, logging flag |
| `ComposeCastClient` | Ktor client: `getScreen` (→ `ScreenDocument`, throws `ComposeCastHttpException`), `listScreens`, `getBrand` |
| `ScreenDocument` | Decoded RC document + `id`, `title` (from `X-Composecast-Screen-Title`), `sizeBytes` |
| `ComposeCastScreen` | Composable: `AndroidView` hosting `RemoteComposePlayer`; `onAction(Action)` |

## Usage Pattern (app-template)

```kotlin
val client = ComposeCastClient(ComposeCastConfig(serverUrl = "http://10.0.2.2:8080"))

val screen by produceState<ScreenDocument?>(null) { value = client.getScreen("main") }

screen?.let {
    ComposeCastScreen(screen = it, modifier = Modifier.fillMaxSize()) { action ->
        when (action) {   // io.composecast.protocol.Action
            is Action.Navigate -> { /* push action.screen */ }
            is Action.OpenUrl -> { /* Intent.ACTION_VIEW */ }
            Action.Reload, is Action.Track, is Action.Custom -> { }
        }
    }
}
```

## Action Routing

Buttons emit RC host actions encoded by `protocol`'s `ActionCodec` (grammar in
`protocol/AGENTS.md`). `ComposeCastScreen` decodes them into typed `Action`s; unknown or malformed
names are logged (`Log.w`, tag `ComposeCast`) and ignored. `protocol` is an `api` dependency because
`Action` is part of this module's public API.

## Logging

`ComposeCastConfig(logEnabled = true)` logs HTTP requests to logcat, tag `ComposeCast`
(`adb logcat -s ComposeCast`). Ktor's default logger needs SLF4J, which Android lacks — the client
installs an `android.util.Log` logger.

## Decisions

- Player: `remote-player-view` wrapped in our own `AndroidView` (~30 lines), not
  `remote-player-compose` (pulls Compose 1.11 + appcompat; possible since AGP 9.5, revisit with
  the Compose upgrade).
- AGP 9 built-in Kotlin: no `org.jetbrains.kotlin.android` plugin; `jvmTarget` = `targetCompatibility`.
- RC player API is `@RestrictTo(LIBRARY_GROUP)` (alpha20–alpha23); keep it inside this module ([ADR 0002](../docs/decisions/0002-confine-restricted-remote-compose-api.md)).
- Document decoding (`RemoteDocument(bytes)`) runs on `Dispatchers.Default`, not the main thread.
- `expectSuccess = true`; `getScreen` turns a non-2xx answer into `ComposeCastHttpException(status)`
  (e.g. 404 unknown screen) so apps can tell "server answered" from "server not reached"
  (any other exception) without depending on Ktor types (Ktor is `implementation`).
- Images are embedded by the server; the player never fetches URLs.
- `minSdk = 26` (required by `remote-player-*`).
