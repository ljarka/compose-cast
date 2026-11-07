# compose-cast

**compose-cast** is an open-source server-driven UI framework for Android built on top of
[Remote Compose](https://developer.android.com/jetpack/compose/remote-compose) (AndroidX).

Screens are defined in a Kotlin DSL on the server, rendered to Remote Compose binary documents
and played natively on Android by the Remote Compose player — no app update needed to change a
screen.

> **Status: experimental.** Built on `androidx.compose.remote` **1.0.0-alpha20**, whose creation
> and player APIs are marked `@RestrictTo(LIBRARY_GROUP)` and may change between alphas.

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
| `compat-tests` | Golden-file compatibility tests between server and player (planned) |

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

## License

Apache 2.0 — see [LICENSE](LICENSE).
