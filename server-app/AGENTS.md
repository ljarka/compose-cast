# server-app — AGENTS.md

Ktor HTTP server. Serves Remote Compose binary documents to Android clients and exposes brand config.

## Scope

This module only. Load `sdk-server/AGENTS.md` for DSL and rendering details.
Do NOT load `sdk-android` or `app-template` unless cross-cutting.

## Key files

| File | Purpose |
|---|---|
| `Main.kt` | `main()` reads env (`PORT`, `COMPOSECAST_DATA_DIR`), builds the registry **before** starting (fail fast); `Application.module(registry)`: CallLogging, ContentNegotiation, StatusPages (logs + JSON 500) |
| `Routing.kt` | All HTTP endpoints; `configureRouting(registry)` |
| `Screens.kt` | `loadScreens(dataDir)` (JSON data dir or demo), `demoScreens()`, `demoBrand`; injectable `ImageLoader` for tests |
| `src/main/resources/logback.xml` | SLF4J provider config — without logback the server logged nothing (not even 500 stack traces) |
| `src/test/.../JsonScreensTest.kt` | `examples/demo` == DSL definitions and brand, canonical, byte-identical RC documents |

`examples/demo/` (repo root) is a complete data directory: `screens/*.json` + `brand.json`, a JSON
copy of `demoScreens()`. `./gradlew :server-app:run` runs from the repo root, so relative
`COMPOSECAST_DATA_DIR` paths work.

## Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `200 ok` (plain text) |
| GET | `/screens/{id}` | RC binary (`application/octet-stream`); `X-Composecast-Screen-Title` header |
| GET | `/screens/{id}/debug` | Plain-text decoded RC tree (dev/debug only) |
| GET | `/screens` | JSON list of screen ids |
| GET | `/brand` | JSON `BrandConfig` |

## Rules

- `Routing.kt` gets the registry as a parameter (testable with Ktor `testApplication { application { module(registry) } }`);
  it never imports `sdk-server` DSL types directly.
- **Changing a demo screen = change `examples/demo` too**; `JsonScreensTest` fails and prints the
  expected file.
- The registry is immutable and built once at startup; editing JSON files needs a restart
  (reload/publish arrives with T-003/T-007).
- `ImageLoader` is injected (default: `HttpImageLoader`) so `compat-tests` can use a fake one
  without network access.
- New endpoints always added in `Routing.kt`; no business logic in `Routing.kt` itself.

## Dependencies

- `:sdk-server` (api) — `composeCast {}`, `ScreenRegistry`, `ImageLoader`
- `:protocol` (api) — `Headers`, `BrandConfig`
- Ktor Netty, ContentNegotiation, CallLogging, StatusPages, kotlinx-serialization-json; logback (runtime)

## Test / run

```bash
./gradlew :server-app:run           # demo screens on :8080; logs every request
COMPOSECAST_DATA_DIR=examples/demo PORT=8080 ./gradlew :server-app:run   # JSON screens
curl http://localhost:8080/health
curl http://localhost:8080/screens
./gradlew :server-app:test          # JsonScreensTest
```
