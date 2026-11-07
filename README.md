# compose-cast

**compose-cast** is an open-source server-driven UI framework for Android built on top of
[Remote Compose](https://developer.android.com/jetpack/compose/remote-compose) (AndroidX).

Screens are defined in a Kotlin DSL on the server, rendered to Remote Compose binary documents
and played natively on Android by the Remote Compose player — no app update needed to change a
screen.

> **Status: experimental.** Built on `androidx.compose.remote` **1.0.0-alpha20**, whose creation
> and player APIs are marked `@RestrictTo(LIBRARY_GROUP)` and may change between alphas.

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
- Android SDK with platform 35 (installed automatically by the Android Gradle Plugin)
- Android 8.0 (API 26) or newer on the device

## Roadmap

### Etap 0 — Technical Spike ✅ (done as part of initial build)
- [x] Verify `remote-creation-jvm` works on plain JVM
- [x] Generate and serve Remote Compose binary documents
- [x] Play `.rc` documents in Android with `RemoteComposePlayer`

### Etap 1 — Repository & CI
- [x] Gradle multi-module structure
- [x] GitHub Actions CI (build + lint)
- [ ] ktlint config + badge

### Etap 2 — Server SDK & Document Endpoints
- [x] DSL facade over `RemoteComposeWriter` (Text, Image, Button, Spacer)
- [x] `GET /screens/{id}` returns binary Remote Compose document
- [x] `GET /screens/{id}/debug` returns decoded document tree
- [x] `GET /brand` brand tokens
- [ ] Component DSL: Row, Column, Box, Card
- [ ] Named colors and brand token integration in components
- [ ] `.screen.kts` hot-reload script host (`server-scripting`)
- [x] Golden compatibility tests (`compat-tests`): screenshot tests, server document → Android player

### Etap 3 — Android SDK
- [x] `RemoteComposePlayer` wrapped in `AndroidView`
- [x] OkHttp fetch with disk cache
- [x] Loading / error states
- [x] Action router (`navigate`, `open_url`, `track`, `custom`)
- [ ] Variable passing to document
- [ ] ETag / conditional requests

### Etap 4 — Brand & Appearance
- [ ] Named colors propagated from `BrandTokens` to screen components
- [ ] `BrandTheme` for Android native frame (status bar, background)
- [ ] Light/dark theme switching
- [ ] `app-template` white-label setup

### Etap 5 — Docker & VPS
- [ ] Docker image (JRE 21, amd64 + arm64)
- [ ] `docker-compose.yml` + `example.env` in `deploy/`
- [ ] Healthcheck, persistent volumes
- [ ] RAM measurement under load

### Etap 6 — Demo & Dev Loop
- [ ] Demo app: product list, product card, welcome screen
- [ ] Dev mode: file watcher + WebSocket push refresh to Android
- [x] Screenshots for README (generated by the screenshot tests)
- [ ] GIF for README

### Etap 7 — Release 0.1.0
- [ ] Docs: Quick start, components reference, action protocol
- [ ] Publish to JitPack + GHCR
- [ ] Tag v0.1.0, CHANGELOG, GitHub Release

### Etap 8 — Admin Panel
A web interface for managing screens without touching code.

**Backend (`admin-server` module):**
- [ ] REST CRUD: `POST/GET/PUT/DELETE /admin/api/screens`
- [ ] Screen definition storage: JSON files on disk (git-friendly)
- [ ] `POST /admin/api/screens/{id}/preview` — returns live binary `.rc` for preview
- [ ] Basic auth via `ADMIN_PASSWORD` env var

**Admin UI (HTML/JS, served by Ktor):**
- [ ] Screen list with create / edit / delete
- [ ] Component palette: Text, Button, Image, Row, Column, Spacer, Card
- [ ] Property editor per component (font, color, padding, action)
- [ ] Publish button — makes screen live on `/screens/{id}`
- [ ] Live device preview via WebSocket push refresh

---

## References

- [Remote Compose — official guide](https://developer.android.com/develop/ui/compose/system/remote-compose)
- [AndroidX releases — compose-remote](https://developer.android.com/jetpack/androidx/releases/compose-remote)
- [armcha/remotecompose — web editor + JVM converter + Android player](https://github.com/armcha/remotecompose)
- [camaelon/remotecompose-experiments — multi-platform players](https://github.com/camaelon/remotecompose-experiments)
- [android/skills — AI-optimised Android skill files](https://github.com/android/skills)

## License

Apache 2.0 — see [LICENSE](LICENSE).
