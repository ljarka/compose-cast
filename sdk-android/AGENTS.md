# sdk-android — AGENTS.md

Android SDK for compose-cast. Downloads Remote Compose documents and plays them with the
AndroidX Remote Compose player (`remote-player-view`).

## Public API

| Class | Purpose |
|---|---|
| `ComposeCastConfig` | Server URL, player version, logging flag, timeouts, `headers` (suspend provider, e.g. `Authorization`; called per request, reserved protocol names ignored), `cacheSize` (ETag LRU, `>= 0`) (T-058) |
| `ComposeCastClient` | `Closeable` Ktor client: `getScreen(id, params)` (→ `ScreenDocument`, ETag revalidation), `listScreens`, `getBrand` (all throw `ComposeCastHttpException` / `ComposeCastUpgradeRequiredException` for non-2xx, T-058); `liveScreen(id, params)`: `Flow<ScreenDocument>` of a live screen's pushes over a WebSocket (T-047) |
| `rememberComposeCastClient(config)` | Composable: a client per composition, closed on dispose / config change (T-058). Several screens or DI: one client per server instead |
| `ComposeCastError` + `Throwable.toComposeCastError()` | `UpgradeRequired(minApiLevel)`, `Http(statusCode)`, `Network(cause)` — classify failures without exception types (T-058) |
| `ScreenDocument` | Decoded RC document + `id`, `title` (from `X-Composecast-Screen-Title`), `sizeBytes`, `liveEveryMs` (from `X-Composecast-Live`; null = not live) |
| `ComposeCastScreen` | Composable: `AndroidView` hosting `RemoteComposePlayer`; `live: Flow<ScreenDocument>?` (applies data updates in place, full documents replace the screen; collected only while the app is started); `onAction(Action)` |

## Usage Pattern (app-shell)

```kotlin
val client = ComposeCastClient(ComposeCastConfig(serverUrl = "http://10.0.2.2:8080"))

val screen by produceState<ScreenDocument?>(null) { value = client.getScreen("main") }
// with params (Action.Navigate.params): GET /screens/movie?id=42, URL-encoded by Ktor
// client.getScreen("movie", mapOf("id" to "42"))

screen?.let {
    ComposeCastScreen(screen = it, modifier = Modifier.fillMaxSize()) { action ->
        when (action) {   // io.composecast.protocol.Action
            is Action.Navigate -> { /* push action (screen + params) */ }
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

- **Explicit API (T-055):** `kotlin { explicitApi() }` — every non-private declaration states
  `public` or `internal`. New helpers default to `internal`; `public` is a published-API decision.
  Audited in T-058: every public declaration here is SDK surface, documented with KDoc.
- **Custom headers (T-058):** `ComposeCastConfig.headers` is a `suspend` provider evaluated per
  request (per WebSocket connection for `liveScreen`), so a refreshed token applies to the next
  request/reconnect. `X-Composecast-Player-Version`, `-Rc-Api-Level`, `-Rc-Profiles` and
  `If-None-Match` are set by the SDK; entries with those names (any case) are dropped, never sent twice.
- **R8 (T-058):** `consumer-rules.pro` (packaged as the AAR's `proguard.txt`) keeps the `protocol`
  serializers that Ktor's `body<T>()` looks up by reflection. RC alpha23 needs no rules (no
  reflection, see `docs/remote-compose.md`). Verified: `app-template` built with `isMinifyEnabled`
  passed `smoke.yaml` and `live.yaml` on the emulator.
- **Live screens (T-047, [ADR 0029](../docs/decisions/0029-live-screens-websocket-data-updates.md)):**
  `ComposeCastClient` uses a single Ktor client on the **OkHttp** engine (T-052) for both REST
  (with configurable connect/request/socket timeouts) and WebSockets (`liveScreen`); same version
  headers as GET; reconnects with backoff 1 s … 30 s;
  stops when the server closes with `CANNOT_ACCEPT` (not live / unknown). `ComposeCastScreen`
  sends data-update frames to `updateDocument`, skips a full frame equal to the shown document
  (`contentHash`) and re-registers action callbacks for other full frames; `repeatOnLifecycle(STARTED)`
  closes the socket in the background. The effect is keyed by the player **value**, not the state:
  reading the state inside the effect connected twice (the factory sets it before the first
  effect runs). Verified on the emulator: one connection per visit, 62 frames/min, Home/Back close.

- Player: `remote-player-view` wrapped in our own `AndroidView` (~30 lines), not
  `remote-player-compose` (pulls Compose 1.11 + appcompat; possible since AGP 9.5, revisit with
  the Compose upgrade).
- AGP 9 built-in Kotlin: no `org.jetbrains.kotlin.android` plugin; `jvmTarget` = `targetCompatibility`.
- RC player API is `@RestrictTo(LIBRARY_GROUP)` (alpha20–alpha23); keep it inside this module ([ADR 0002](../docs/decisions/0002-confine-restricted-remote-compose-api.md)).
- Document decoding (`RemoteDocument(bytes)`) runs on `Dispatchers.Default`, not the main thread.
- ETag cache (T-003): last document per (screen, params) in memory, LRU 32; requests carry
  `If-None-Match`; on `304` the cached **bytes** are decoded into a new `ScreenDocument` (a played
  RC document keeps runtime state, so instances are never reused). Disk cache: Etap 7.
- `expectSuccess = true` (per request `false` in `getScreen`, where 304 is expected); `getScreen` turns a non-2xx answer into `ComposeCastHttpException(status)`
  (e.g. 404 unknown screen) so apps can tell "server answered" from "server not reached"
  (any other exception) without depending on Ktor types (Ktor is `implementation`).
- Images are embedded by the server; the player never fetches URLs.
- `minSdk = 26` (required by `remote-player-*`).

## Publishing (T-024)

Published as `io.composecast:sdk-android:<version>` (`maven-publish`; repository setup in the root
`build.gradle.kts`: dry run into `build/maven-dry-run`, Reposilite in the release workflow). Its
public API is used by published apps: a breaking change needs a new compose-cast version.

## Tests (T-050)

`ComposeCastClientTest` runs on the JVM with `ktor-client-mock` (via internal constructor) and covers
200/304 ETag caching, LRU eviction, `cacheSize` 0 / negative, 426 Upgrade Required, HTTP errors,
`listScreens`, `getBrand`, custom headers vs. reserved ones, `Closeable` and the `ComposeCastError`
mapping. `liveScreen` headers are tested against a real Ktor Netty server on a random port with the
real OkHttp engine (`MockEngine` has no WebSocket support).
Run via `./gradlew :sdk-android:testDebugUnitTest` (included in `./scripts/verify.sh`).
