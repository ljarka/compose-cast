# server-app — AGENTS.md

Ktor HTTP server. Serves Remote Compose binary documents to Android clients and exposes brand config.

## Scope

This module only. Load `sdk-server/AGENTS.md` for DSL and rendering details.
Do NOT load `sdk-android` or `app-template` unless cross-cutting.

## Key files

| File | Purpose |
|---|---|
| `Main.kt` | `embeddedServer(Netty, port=8080)`, installs `ContentNegotiation` (JSON), calls `configureRouting()` |
| `Routing.kt` | All HTTP endpoints; reads from `registry` |
| `Screens.kt` | `demoScreens()` — the screen definitions; `registry` lazy val; injectable `ImageLoader` for tests |

## Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `200 ok` (plain text) |
| GET | `/screens/{id}` | RC binary (`application/octet-stream`); `X-Composecast-Screen-Title` header |
| GET | `/screens/{id}/debug` | Plain-text decoded RC tree (dev/debug only) |
| GET | `/screens` | JSON list of screen ids |
| GET | `/brand` | JSON `BrandConfig` |

## Rules

- `Routing.kt` reads from `registry`; it never imports `sdk-server` DSL types directly.
- `Screens.kt` owns the demo screen definitions and the `registry` lazy val.
- `ImageLoader` is injected (default: `HttpImageLoader`) so `compat-tests` can use a fake one
  without network access.
- New endpoints always added in `Routing.kt`; no business logic in `Routing.kt` itself.

## Dependencies

- `:sdk-server` (api) — `composeCast {}`, `ScreenRegistry`, `ImageLoader`
- `:protocol` (api) — `Headers`, `BrandConfig`
- Ktor Netty, ContentNegotiation, kotlinx-serialization-json

## Test / run

```bash
./gradlew :server-app:run           # starts on :8080
curl http://localhost:8080/health
curl http://localhost:8080/screens
```
