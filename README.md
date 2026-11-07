# compose-cast

**compose-cast** is an open-source server-driven UI framework for Android built on top of [Remote Compose](https://developer.android.com/jetpack/compose/remote-compose) (AndroidX).

It lets you serve dynamic, fully composable screens from a backend — no app updates required.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Server Side                        │
│  server-app (Ktor)  ──►  sdk-server (DSL facade)    │
│         │                       │                    │
│  server-scripting (.screen.kts) │                    │
│                            protocol (shared types)   │
└─────────────────────────────────────────────────────┘
                          │
                   HTTP  (Remote Compose binary)
                          │
┌─────────────────────────────────────────────────────┐
│                  Android Client                      │
│  app-template  ──►  sdk-android  ──►  RemotePlayer  │
└─────────────────────────────────────────────────────┘
```

## Modules

| Module | Description |
|---|---|
| `protocol` | Shared constants and serializable types (actions, brand tokens) |
| `sdk-server` | Server-side DSL facade for building Remote Compose screens |
| `server-scripting` | `.screen.kts` script host for hot-reloadable screen definitions |
| `server-app` | Ktor HTTP server with `/screens/{id}` and `/brand` endpoints |
| `sdk-android` | Android SDK for fetching and rendering server-driven screens |
| `app-template` | Demo Android application |
| `compat-tests` | Golden-file compatibility tests between server and player |

## Quick Start

```bash
./gradlew :server-app:run
curl http://localhost:8080/health
```

## Requirements

- JDK 21+
- Android SDK 35 (for Android modules)

## License

Apache 2.0 — see [LICENSE](LICENSE).
