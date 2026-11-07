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
| `protocol` | Shared constants (`Actions`, `Headers`), `BrandConfig`, `BrandTokens` | done |
| `sdk-server` | DSL → RC documents; `ScreenRegistry`; `ImageLoader` | done |
| `server-scripting` | `.screen.kts` script host (hot-reload) | empty |
| `server-app` | Ktor HTTP server; screens defined in `Screens.kt` | done |
| `sdk-android` | Fetch RC documents, play them with `remote-player-view` | done |
| `app-template` | Demo app: navigation, back stack, error state | done |
| `compat-tests` | Screenshot tests: server document → Android player; goldens shown in README | done (see its AGENTS.md) |

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
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -gpu host &   # -gpu host matters, see below
./gradlew :app-template:installDebug
adb shell am start -n io.composecast.demo/.MainActivity
adb exec-out screencap -p > screen.png            # look at it; tests alone do not catch layout bugs
adb shell uiautomator dump /sdcard/u.xml && adb shell cat /sdcard/u.xml   # texts are in a11y tree
```

Full build = what CI runs (`.github/workflows/ci.yml`):
`:protocol:build :sdk-server:build :server-scripting:build :server-app:build`
then `:sdk-android:assembleDebug :sdk-android:lintDebug :app-template:assembleDebug :app-template:lintDebug`
then `:compat-tests:verifyRoborazziDebug` (screenshot goldens; on failure CI uploads the diffs).
**Any visual change** (renderer, DSL defaults, dependency bumps) must keep the screenshots green
or re-record them (`:compat-tests:recordRoborazziDebug`), look at the PNGs, and commit them —
they are the README images.
Lint must stay green. `RestrictedApi` is downgraded to a warning **only in `sdk-android`** (the
deliberate RC player usage). Compose lint checks are active (verified with a deliberately wrong
`produceState`); with Compose 1.7.5 under AGP 9.5 they silently did not run.

Emulator: started from a terminal without `-gpu host` it was ~20x slower (activity start 7–10 s,
100% janky frames system-wide); with `-gpu host` start is ~0.4 s. If the app looks slow on the
emulator, check this before blaming the code.

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
- **Build toolchain:** AGP 9.5.1, Gradle 9.6.0, Kotlin/KGP 2.4.21 (AGP 9.5 needs ≥ 2.2.10).
  Compose via BOM (`platform(libs.compose.bom)` in every module using Compose), compileSdk 37,
  targetSdk 36. Ktor 3.6.1, kotlinx-serialization 1.12.0.
  AGP 9 **built-in Kotlin**: Android modules must not apply `org.jetbrains.kotlin.android`;
  `jvmTarget` follows `compileOptions.targetCompatibility` (11), so no `kotlin {}` block there.
  New DSL is on (legacy variant API unavailable). `org.gradle.tooling.parallel=true` was added by
  Android Studio for Gradle 9.4+ sync.
- **Player wrapper:** `remote-player-view` inside our own `AndroidView`, not `remote-player-compose`.
  Since AGP 9.5 switching would be possible (it needs Compose 1.11, compileSdk 36), but it adds
  appcompat + Compose 1.11 for little gain; revisit together with the Compose upgrade.
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

Numbering here is the order of work, **not** the README roadmap's "Etap 0–8" (the owner's plan;
the README roadmap is the source of truth for what to build next).

1. **Initial state (commits up to `2f2103c`).** Despite the README, Remote Compose was not used:
   the server sent JSON `ScreenModel`, `sdk-android` drew it with a hand-written Compose renderer,
   RC dependencies were declared but unused. Android modules had never been compiled. The owner
   considered this a fundamental mistake — new components would require app releases, which is
   exactly what Remote Compose is meant to avoid.
2. **Server emits RC (`99d8979`).** `RemoteComposeRenderer`, binary `/screens/{id}`,
   `/debug` endpoint, build-time validation, tests decoding documents with `remote-core`.
   Also `c39bb91`: debug-only cleartext HTTP config so the emulator can reach `http://10.0.2.2`
   (targetSdk 35 blocks cleartext by default).
3. **App plays RC.** `sdk-android` rewritten around `RemoteComposePlayer`
   (`ScreenDocument`, `ComposeCastScreen`), JSON renderer deleted, `expectSuccess = true` (404 is a
   clear error), `minSdk 26`. App: back stack + `BackHandler`, survives rotation. Server: embedded
   images, dp/sp units, `height` workaround (gotchas 1–3), vertical scroll.
4. **Cleanup.** Removed legacy JSON `ScreenModel`/`ComponentModel`; Android build + lint
   in CI; `protocol` exposes serialization as `api`; `jvmTarget` → `kotlin.compilerOptions`;
   unused version-catalog entries removed (`compose-bom`, `compose-compiler`, `ktlint`,
   `kotlin-stdlib`); README rewritten.
5. **AGP 8.7.0 → 9.5.1**, Gradle 8.10 → 9.6.0, migrated to built-in Kotlin (removed
   `kotlin-android` plugin and the explicit `jvmTarget`). Removed the `@SuppressLint` for the
   `produceState` false positive (the check no longer runs, see Run & Verify).
6. **Screenshot tests** in `compat-tests` (Robolectric + Roborazzi), goldens in the README,
   verified in CI. `demoScreens()` extracted in `server-app`; `ScreenDocument.fromBytes` added.
7. **Dependency refresh:** Compose 1.7.5 → BOM 2026.09.00, activity-compose 1.13.0, Kotlin
   2.4.21, Ktor 3.6.1, kotlinx-serialization 1.12.0, compileSdk 37 / targetSdk 36. Remote Compose
   kept at alpha20. Screenshots re-recorded: RC content pixel-identical, only the Material3
   TopAppBar is shorter under Robolectric (status-bar inset); on the emulator the app is unchanged.

## Open Work

The plan is the README roadmap (Vision + Etap 0–10); read the Vision principles before adding
features. Next in order: **Etap 2 — Foundations** (RC bump, version negotiation, dynamic
per-request screens, ETag, typed actions). Technical leftovers not in the roadmap:

- Gradle configuration cache (Gradle 9 suggests it on every build).
- Images are fetched once at server start (picsum.photos returns a random image each time) —
  goes away with dynamic screens.

## Git

Work goes directly on `main` (project convention). Commit/push only when the owner asks.

```bash
git add <paths> && git commit -m "..."
git push
```
