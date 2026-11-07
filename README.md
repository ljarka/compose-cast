# compose-cast

**compose-cast** is an open-source server-driven UI framework for Android built on top of
[Remote Compose](https://developer.android.com/jetpack/compose/remote-compose) (AndroidX).

Screens are defined in a Kotlin DSL on the server, rendered to Remote Compose binary documents
and played natively on Android by the Remote Compose player — no app update needed to change a
screen.

> **Status: experimental.** Built on `androidx.compose.remote` **1.0.0-alpha20**, whose creation
> and player APIs are marked `@RestrictTo(LIBRARY_GROUP)` and may change between alphas.

## Vision

**DivKit-style server-driven UI for Android, with Remote Compose as both the wire format and the
renderer.**

[DivKit](https://divkit.tech) defines its own JSON layout format and ships a renderer per platform.
compose-cast does not build a renderer: AndroidX Remote Compose already has a binary document
format and a player, including the runtime primitives DivKit is known for (variables, expressions,
states, local actions, animation). compose-cast is the product layer around it:

- **Authoring** — Kotlin DSL today; later scripts (`.screen.kts`) and an admin panel, all compiled
  to the same internal screen model.
- **Delivery** — server that renders documents per request and per player capability.
- **Integration** — Android SDK: fetching, caching, actions, variables, images, navigation.
- **Tooling** — debug dump, screenshot tests, preview, dev loop.

Principles:

1. Remote Compose is the only wire format; compose-cast never draws UI itself.
2. A screen change must never break an app already in users' hands: the server renders for the
   capabilities the player reports.
3. Interaction runs on the device (RC variables, expressions, states) — no server round trip for
   a toggle or a tab.
4. Everything visual is screenshot-tested.
5. Android-first: the official RC player is Android-only (other platforms are community
   experiments, see References).

### DivKit concepts → Remote Compose → compose-cast

| DivKit concept | Remote Compose primitive (alpha) | compose-cast |
|---|---|---|
| Element tree (containers, text, image) | `Column`/`Row`/`Box`/`Flow`, `Text`, `Image`, `Canvas` | Text, Image, Button, Spacer ✅; containers ⏳ Etap 3 |
| Templates | Server-side Kotlin functions; RC macros (`defineMacro`/`inflate`) | ⏳ Etap 4 |
| Variables | Named variables, set by the host (`setUserLocal*` on the player) | ⏳ Etap 4 |
| Expressions | On-device float/text expressions, conditional operations | used internally for dp/sp; DSL ⏳ Etap 4 |
| States | `StateLayout` + local value-change actions | ⏳ Etap 4 |
| Actions | Host actions (named) + local value changes | host actions as `type:payload` ✅; typed ⏳ Etap 4 |
| Light/dark, theming | Themed colors in the document | ⏳ Etap 5 |
| Custom elements / extensions | `Custom` component + player custom support | ⏳ later |
| Loading new markup without release | Whole document per request; player `updateDocument` | ✅ per screen; partial updates ⏳ investigate |
| Visibility actions (analytics) | no equivalent found | ⏳ SDK-side, Etap 6 |
| Platforms: Android, iOS, web, KMP | Android player only | Android only (by design) |
| Playground / editor | — | `/debug` dump ✅, PNG preview ⏳ Etap 7, admin panel Etap 10 |

## Screenshots

The demo screens from [`Screens.kt`](server-app/src/main/kotlin/io/composecast/server/Screens.kt),
rendered to Remote Compose documents by the server SDK and played by the Android SDK's
`RemoteComposePlayer`:

<table>
  <tr>
    <td align="center"><img src="compat-tests/src/test/screenshots/main.png" width="270" alt="Home screen"><br><code>main</code></td>
    <td align="center"><img src="compat-tests/src/test/screenshots/onboarding.png" width="270" alt="Get Started screen"><br><code>onboarding</code></td>
  </tr>
</table>

These images are the golden files of the screenshot tests in `compat-tests`
(Robolectric + [Roborazzi](https://github.com/takahirom/roborazzi), no emulator or network;
remote images are replaced by a generated gradient). CI fails when rendering changes.

```bash
./gradlew :compat-tests:verifyRoborazziDebug   # compare with the goldens
./gradlew :compat-tests:recordRoborazziDebug   # accept intentional changes, then commit the PNGs
```

## Architecture

```
┌──────────────────────────── Server (JVM) ────────────────────────────┐
│  server-app (Ktor)  ──►  sdk-server: DSL → Remote Compose document    │
│                          (images fetched and embedded at render time) │
│  server-scripting (.screen.kts, planned)      protocol (shared types) │
└───────────────────────────────────────────────────────────────────────┘
                                  │  HTTP: application/octet-stream
                                  ▼  (Remote Compose binary document)
┌──────────────────────────── Android ─────────────────────────────────┐
│  app-template  ──►  sdk-android  ──►  RemoteComposePlayer             │
│                     button taps come back as host actions,            │
│                     e.g. "navigate:onboarding"                        │
└───────────────────────────────────────────────────────────────────────┘
```

## Defining screens

```kotlin
val registry = composeCast {
    brand(name = "ComposeCast Demo", primaryColor = "#6200EE", secondaryColor = "#03DAC6")

    screen("main") {
        title = "Home"
        text("Welcome to ComposeCast", style = "h1")   // h1 | h2 | h3 | body | caption
        image("https://picsum.photos/400/200", alt = "Hero image")
        button("Get started", action = "navigate:onboarding")
    }
}
```

Actions: `navigate:<screen>`, `open_url:<url>`, `track:<event>`, `custom:<payload>`.
Invalid definitions (unknown style or action, duplicate id, bad color) fail at server start.
Demo screens live in `server-app/src/main/kotlin/io/composecast/server/Screens.kt`.

## Modules

| Module | Description |
|---|---|
| `protocol` | Shared constants (actions, headers) and serializable brand types |
| `sdk-server` | DSL that renders screens to Remote Compose documents |
| `server-scripting` | `.screen.kts` script host for hot-reloadable screens (planned) |
| `server-app` | Ktor HTTP server |
| `sdk-android` | Fetches documents and plays them with the Remote Compose player |
| `app-template` | Demo Android app with navigation and back stack |
| `compat-tests` | Screenshot tests: server document → Android player, golden PNGs used in this README |

## Quick start

Server:

```bash
./gradlew :server-app:run
curl http://localhost:8080/screens                 # ["main","onboarding"]
curl http://localhost:8080/screens/main/debug      # human-readable document tree
```

| Endpoint | Response |
|---|---|
| `GET /health` | `ok` |
| `GET /brand` | Brand JSON |
| `GET /screens` | JSON list of screen ids |
| `GET /screens/{id}` | Remote Compose document; title in `X-Composecast-Screen-Title` |
| `GET /screens/{id}/debug` | Decoded document as text |

Android (emulator; it reaches the server at `http://10.0.2.2:8080`):

1. Keep the server running.
2. Open the project root in Android Studio and run the **app-template** configuration,
   or `./gradlew :app-template:installDebug`.

For a physical device change `SERVER_URL` in `app-template/.../MainActivity.kt`.
Cleartext HTTP is allowed only in debug builds and only for local addresses.

## Requirements

- JDK 21
- Android SDK with platform 37 (installed automatically by the Android Gradle Plugin)
- Android 8.0 (API 26) or newer on the device

## Roadmap

### Etap 0 — Technical Spike ✅
- [x] Verify `remote-creation-jvm` works on plain JVM
- [x] Generate and serve Remote Compose binary documents
- [x] Play `.rc` documents in Android with `RemoteComposePlayer`

### Etap 1 — Repository, CI & Tests ✅
- [x] Gradle multi-module structure
- [x] GitHub Actions CI: build, lint, screenshot verification
- [x] Screenshot tests server → player (`compat-tests`), images in this README
- [x] Current toolchain: AGP 9.5, Gradle 9.6, Kotlin 2.4, Compose BOM
- [ ] ktlint config + badge

### Etap 2 — Foundations (before more features)
Things the rest depends on; cheaper now than after the DSL grows.
- [ ] Bump Remote Compose alpha20 → latest alpha (re-check the known alpha20 workarounds)
- [ ] **Version negotiation:** the SDK reports the RC document API level/profiles its player
  supports; the server renders for it or returns a clear "update required" response
- [ ] **Dynamic screens:** a screen is a function of the request (path/query params, user, locale),
  rendered per request with caching — today screens are pre-rendered once at server start
- [ ] `ETag` / `If-None-Match` on documents
- [ ] Typed actions in `protocol` instead of `"type:payload"` strings

### Etap 3 — Component DSL
- [x] Text, Image, Button, Spacer
- [ ] Containers: Row, Column, Box, Card, Divider; nesting
- [ ] Layout: size, weight, alignment, padding; text color, alignment, max lines
- [ ] Horizontal scrolling row / gallery
- [ ] Accessibility: content descriptions, roles
- [ ] Image strategy: embed small static images (today); for lists and large images let the SDK
  load them (Coil) and supply them as named bitmaps

### Etap 4 — Variables, State & Templates (the DivKit core)
- [ ] Host → document variables (e.g. user name, cart count) via RC named variables
- [ ] Local state without a server round trip: toggles, tabs, expand/collapse (`StateLayout`)
- [ ] Expressions in the DSL: conditional visibility, computed text
- [ ] Templates: reusable Kotlin components; RC macros to shrink documents
- [ ] Actions: `set_variable`, `reload`, `navigate`, `open_url`, `track`

### Etap 5 — Brand & Appearance
- [ ] Brand tokens (`BrandTokens`) → named colors in documents
- [ ] Light/dark via RC themed colors (inside the document)
- [ ] `BrandTheme` for the native frame (status bar, background)
- [ ] `app-template` white-label setup

### Etap 6 — Android SDK
- [x] `RemoteComposePlayer` wrapped in `AndroidView`
- [x] Ktor fetch, document decoding off the main thread
- [x] Loading / error states
- [x] Action router (`navigate`, `open_url`, `track`, `custom`)
- [ ] HTTP cache, offline last-known screen, prefetch
- [ ] Navigation helper (back stack) moved from `app-template` into the SDK
- [ ] Analytics hooks: actions and screen/element visibility
- [ ] Variable API (`setVariable`) backing Etap 4

### Etap 7 — Dev Loop & Tooling
- [ ] `.screen.kts` hot reload (`server-scripting`) + WebSocket push refresh to the app
- [ ] PNG preview endpoint (`/screens/{id}/preview.png`) rendered on the JVM like the screenshot
  tests — RC has no official web player, so this is what a browser preview can show
- [ ] Demo app: product list, product card, welcome screen (exercises dynamic screens + variables)
- [ ] GIF for README

### Etap 8 — Docker & VPS
- [ ] Docker image (JRE 21, amd64 + arm64)
- [ ] `docker-compose.yml` + `example.env` in `deploy/`
- [ ] Healthcheck, persistent volumes
- [ ] RAM measurement under load (per-request rendering + image embedding)

### Etap 9 — Release 0.1.0-alpha
- [ ] Docs: Quick start, components reference, action protocol, compatibility policy
- [ ] Publish SDKs (JitPack/Maven) + server image (GHCR)
- [ ] Tag v0.1.0-alpha, CHANGELOG, GitHub Release

### Etap 10 — Admin Panel
A web interface for managing screens without touching code.

**Prerequisites:** a serializable screen schema (the same model the DSL builds, so DSL, scripts and
the panel produce identical documents) and the PNG preview from Etap 7.

**Backend (`admin-server` module):**
- [ ] REST CRUD: `POST/GET/PUT/DELETE /admin/api/screens`
- [ ] Screen definition storage: JSON files on disk (git-friendly)
- [ ] `POST /admin/api/screens/{id}/preview` — PNG preview (and binary `.rc` for devices)
- [ ] Basic auth via `ADMIN_PASSWORD` env var

**Admin UI (HTML/JS, served by Ktor):**
- [ ] Screen list with create / edit / delete
- [ ] Component palette: Text, Button, Image, Row, Column, Spacer, Card
- [ ] Property editor per component (font, color, padding, action)
- [ ] Publish button — makes screen live on `/screens/{id}`
- [ ] Live device preview via WebSocket push refresh

---

## References

- [DivKit — the model this project follows](https://divkit.tech/docs/en/)
- [Remote Compose — official guide](https://developer.android.com/develop/ui/compose/system/remote-compose)
- [AndroidX releases — compose-remote](https://developer.android.com/jetpack/androidx/releases/compose-remote)
- [armcha/remotecompose — web editor + JVM converter + Android player](https://github.com/armcha/remotecompose)
- [camaelon/remotecompose-experiments — multi-platform players](https://github.com/camaelon/remotecompose-experiments)
- [android/skills — AI-optimised Android skill files](https://github.com/android/skills)

## License

Apache 2.0 — see [LICENSE](LICENSE).
