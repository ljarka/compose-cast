# compose-cast — Project AGENTS.md

Server-driven UI framework for Android built on **AndroidX Remote Compose**.
The server renders screens to Remote Compose (RC) binary documents; the Android app plays them
with the RC player. **Remote Compose end-to-end is a hard requirement** — do not reintroduce a
JSON format with a hand-written renderer (see History for why this matters).

## Architecture

```
Screens.kt (DSL)                       server-app                 sdk-android            app-template
composeCast { screen("main") {...} }   GET /screens/{id}          ComposeCastClient      MainActivity
        │                              application/octet-stream   getScreen(id)          back stack
        ▼                              X-Composecast-Screen-Title       │                      │
sdk-server: ScreenBuilder → ScreenNode ──► RC bytes ──── HTTP ────► RemoteDocument ──► ComposeCastScreen
            RemoteComposeRenderer                                  (decoded off main)   (RemoteComposePlayer
            (remote-creation DSL)                                                        in AndroidView)
                                                    button tap ◄── hostAction("navigate:onboarding")
                                                                   → onAction("navigate", "onboarding")
```

## Module Map

| Module | Scope | Status |
|---|---|---|
| `protocol` | Shared constants (`Actions`, `Headers`), `BrandConfig`, `BrandTokens` | done; still contains legacy `ScreenModel`/`ComponentModel` (unused, to delete) |
| `sdk-server` | DSL → RC documents; `ScreenRegistry`; `ImageLoader` | done |
| `server-scripting` | `.screen.kts` script host (hot-reload) | empty |
| `server-app` | Ktor HTTP server; screens defined in `Screens.kt` | done |
| `sdk-android` | Fetch RC documents, play them with `remote-player-view` | done |
| `app-template` | Demo app: navigation, back stack, error state | done |
| `compat-tests` | Golden-file compatibility tests | empty |

## Task Strategy

Each task targets exactly one module. Load only the relevant module's `AGENTS.md` + its direct deps.
Never load the whole project. Load root `AGENTS.md` only for cross-module decisions.
Module `AGENTS.md` files hold the detailed decisions; this file holds the cross-cutting ones.

## Environment

- JDK 21: Homebrew `openjdk@21` (keg-only). Non-interactive shells (agents) do not load `~/.zshrc`,
  so prefix Gradle: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew ...`
- Android SDK: `~/Library/Android/sdk` (`local.properties`, gitignored). AVD: `Medium_Phone_API_37.0`.
- `gh` CLI is not installed; check CI at https://github.com/ljarka/compose-cast/actions

## Run & Verify

```bash
./gradlew :server-app:run
curl http://localhost:8080/screens                 # ["main","onboarding"]
curl http://localhost:8080/screens/main/debug      # decoded RC tree, human-readable
./gradlew :sdk-server:test                         # decodes documents with remote-core

# Android, end to end (server must be running; emulator reaches it at 10.0.2.2:8080)
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 &
./gradlew :app-template:installDebug
adb shell am start -n io.composecast.demo/.MainActivity
adb exec-out screencap -p > screen.png            # look at it; tests alone do not catch layout bugs
adb shell uiautomator dump /sdcard/u.xml && adb shell cat /sdcard/u.xml   # texts are in a11y tree
```

Full build (what CI should run): `:protocol:build :sdk-server:build :server-scripting:build
:server-app:build :compat-tests:build :sdk-android:assembleDebug :app-template:assembleDebug`.
CI currently builds JVM modules only (`.github/workflows/ci.yml`) — Android modules are missing.

Endpoints:
- `GET /health` → `ok`
- `GET /brand` → BrandConfig JSON
- `GET /screens` → JSON list of screen ids
- `GET /screens/{id}` → RC document (`application/octet-stream`), title in
  `X-Composecast-Screen-Title` (URL-encoded UTF-8); unknown id → 404 JSON
- `GET /screens/{id}/debug` → plain-text dump of the decoded document

## Key Decisions

- **RC library:** `androidx.compose.remote:*:1.0.0-alpha20`. Its whole creation *and* player API is
  `@RestrictTo(LIBRARY_GROUP)` with no public alternative. Rules: creation API only in
  `sdk-server/RemoteComposeRenderer.kt`; player API only in `sdk-android`. Never expose RC types in
  public API. When bumping the version, re-run tests **and** check the emulator screenshot.
- **DSL stays ours:** `text`/`image`/`button`/`spacer` in `ScreenBuilder` → internal `ScreenNode` →
  renderer. Invalid input (text style, action type, duplicate id, color) fails at server start.
- **Actions:** button = clickable `Box` + `hostAction("type:payload")`; types in `protocol/Actions.kt`
  (`navigate`, `open_url`, `track`, `custom`). The app splits on the first `:`.
- **Screens are rendered once** in `composeCast {}` (after brand is known) and served from memory.
- **Player wrapper:** `remote-player-view` inside our own `AndroidView`, not `remote-player-compose`
  (it pulls Compose 1.11 → compileSdk 36 + AGP upgrade). AGP is still 8.7.0; upgrading is a
  separate, planned change.
- Ktor on both sides; `kotlinx.serialization` for the JSON endpoints (`/brand`, `/screens`).

## Remote Compose gotchas (alpha20) — read before touching rendering

Each of these was found by running on the emulator, not by unit tests.

1. **URL images do not work in the player.** Disabled by default (`Limits.ENABLE_IMAGE_URLS`),
   size-checked against the 1x1 the DSL declares (`CHECK_DATA_SIZE`), and fetched synchronously on
   the main thread. → The server fetches images at render time (`ImageLoader`) and **embeds** the
   original bytes with the real size. Failed fetch → alt text placeholder + warning, server keeps
   running. Tests use a fake `ImageLoader` (no network).
2. **Units.** Default density behavior mixes px and dp, so everything rendered tiny. Header
   `DOC_DENSITY_BEHAVIOR = DP` scales only padding, borders and clip radii. Font sizes are emitted
   as `FONT_SIZE * sp / 14` (player sets FONT_SIZE = 14sp in px, incl. user font scale) and heights
   as `density * dp`, both evaluated on the device. Use the `sp()`/`dp()` helpers in the renderer.
3. **`height(RcFloat)` bug:** the modifier reads the expression id without writing it → document
   gets `Height(EXACT, 0.0)` and the element disappears. Wrap with `written(...)`. A test asserts
   no `Height(EXACT, 0.0)` appears.
4. `RemoteComposeWriter`'s `remoteBitmapUrl` / `createRcBuffer` signatures: read the sources jar
   (`https://dl.google.com/android/maven2/androidx/compose/remote/<artifact>/1.0.0-alpha20/<artifact>-1.0.0-alpha20-sources.jar`)
   instead of guessing; most relevant logic is in `remote-core` (`CoreDocument`, layout managers).

## History (why the code looks like this)

1. **Initial state (commits up to `2f2103c`).** Despite the README, Remote Compose was not used:
   the server sent JSON `ScreenModel`, `sdk-android` drew it with a hand-written Compose renderer,
   RC dependencies were declared but unused. Android modules had never been compiled. The owner
   considered this a fundamental mistake — new components would require app releases, which is
   exactly what Remote Compose is meant to avoid.
2. **Stage 1 — server emits RC (`99d8979`).** `RemoteComposeRenderer`, binary `/screens/{id}`,
   `/debug` endpoint, build-time validation, tests decoding documents with `remote-core`.
   Also `c39bb91`: debug-only cleartext HTTP config so the emulator can reach `http://10.0.2.2`
   (targetSdk 35 blocks cleartext by default).
3. **Stage 2 — app plays RC.** `sdk-android` rewritten around `RemoteComposePlayer`
   (`ScreenDocument`, `ComposeCastScreen`), JSON renderer deleted, `expectSuccess = true` (404 is a
   clear error), `minSdk 26`. App: back stack + `BackHandler`, survives rotation. Server: embedded
   images, dp/sp units, `height` workaround (gotchas 1–3), vertical scroll.

## Open Work (in suggested order)

1. **Stage 3 cleanup:** delete `ScreenModel`/`ComponentModel` from `protocol`; add Android modules
   to CI; update `README.md` (architecture is right now, but it lacks Android run steps, the
   `/debug` endpoint and the alpha20 caveats); remove unused entries in `libs.versions.toml` (`compose-bom`, `compose-compiler`,
   `ktlint`, `kotlin-stdlib`); `protocol` should expose serialization as `api`.
2. **AGP upgrade** (8.7.0 → current), `jvmTarget` → `compilerOptions`; then reconsider
   `remote-player-compose`.
3. **Version negotiation:** client sends `X-Composecast-Player-Version`, server ignores it. Needed
   before any app ships, since RC document features must match the player.
4. **Brand in the app:** `/brand` and `getBrand()` exist but the app uses default `MaterialTheme`.
5. `compat-tests` (golden RC documents), `server-scripting` (`.screen.kts` hot-reload; note screens
   are pre-rendered, so reload must rebuild the registry).
6. Images are fetched once at server start (picsum.photos returns a random image each time).

## Git

Work goes directly on `main` (project convention). Commit/push only when the owner asks.

```bash
git add <paths> && git commit -m "..."
git push
```
