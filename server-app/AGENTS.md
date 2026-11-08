# server-app — AGENTS.md

Ktor HTTP server. Serves Remote Compose binary documents to Android clients and exposes brand config.

## Scope

This module only. Load `sdk-server/AGENTS.md` for DSL and rendering details.
Do NOT load `sdk-android` or `app-template` unless cross-cutting.

## Key files

| File | Purpose |
|---|---|
| `Main.kt` | `main()` reads env (`PORT`, `COMPOSECAST_DATA_DIR`), builds the registry **before** starting (fail fast); `Application.module(LiveScreens)`: CallLogging, ContentNegotiation, StatusPages (`ScreenParameterException` → 400, other → logged JSON 500) |
| `Routing.kt` | All HTTP endpoints; `configureRouting(screens: LiveScreens)`; ETag / `If-None-Match` |
| `LiveScreens.kt` | `AtomicReference` holder of the served `ScreenRegistry`; `replace()` swaps it atomically (admin publish, reloads) |
| `Screens.kt` | `loadScreens(dataDir)` (JSON data dir or demo), `demoScreens()` (incl. dynamic `echo`), `demoBrand`; injectable `ImageLoader` for tests |
| `src/main/resources/logback.xml` | SLF4J provider config — without logback the server logged nothing (not even 500 stack traces) |
| `src/test/.../RoutingTest.kt` | Ktor `testApplication`: 304, query params (non-ASCII), 400/404, 50 concurrent requests, registry swap |
| `src/test/.../JsonScreensTest.kt` | `examples/demo` == DSL definitions and brand, canonical, byte-identical RC documents |

`examples/demo/` (repo root) is a complete data directory: `screens/*.json` + `brand.json`, a JSON
copy of `demoScreens()`. `./gradlew :server-app:run` runs from the repo root, so relative
`COMPOSECAST_DATA_DIR` paths work.

## Docker (T-009)

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage: Gradle `installDist` with `-Pcomposecast.serverOnly=true` (no Android SDK) on `$BUILDPLATFORM` → `eclipse-temurin:21.0.13_9-jre-alpine`, non-root `composecast`, `COMPOSECAST_DATA_DIR=/data` (no demo fallback), healthcheck = busybox `wget /health` |
| `/docker-compose.yml` (root) | Dev/demo: builds the image, mounts `${COMPOSECAST_SCREENS:-./examples/demo}:/data:ro`, optional `.env` |
| `/.dockerignore` (root) | Context = repo root; excludes Android modules, docs, build output, PNGs |
| `/scripts/verify-docker.sh` | `verify.sh --docker`: build, healthy < 30 s, `/screens`, size < 250 MB |

- The build stage must `chmod -R a+rX` the install: Gradle-cache jars are 0600 → `NoClassDefFoundError` as non-root.
- Pin base image tags (`21.0.13_9-…`); bump them with the JDK. Apps pull the published image (T-023, ADR 0017).

## Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `200 ok` (plain text) |
| GET | `/screens/{id}?k=v` | RC binary (`application/octet-stream`); query = `RenderContext.params`; headers `X-Composecast-Screen-Title`, `ETag`, `Cache-Control: no-cache`; `If-None-Match` match → `304`; missing param of a dynamic screen → `400` |
| GET | `/screens/{id}/debug?k=v` | Plain-text decoded RC tree + etag (dev/debug only) |
| GET | `/screens` | JSON list of screen ids (static and dynamic) |
| GET | `/brand` | JSON `BrandConfig` |

## Rules

- `Routing.kt` gets the registry as a parameter (testable with Ktor `testApplication { application { module(registry) } }`);
  it never imports `sdk-server` DSL types directly.
- **Changing a demo screen = change `examples/demo` too**; `JsonScreensTest` fails and prints the
  expected file.
- Routing reads `LiveScreens.current` per request; nothing swaps it yet (editing JSON files needs a
  restart) — admin publish (T-007) will build a new registry and `replace()` it.
- `examples/demo` has no JSON for `echo`: dynamic screens are DSL-only until data bindings (T-006).
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
docker compose up -d                # the image on examples/demo (COMPOSECAST_SCREENS=… for others)
./scripts/verify.sh --docker        # image smoke test
```
