# server-app — AGENTS.md

Ktor HTTP server. Serves Remote Compose binary documents to Android clients and exposes brand config.

## Scope

This module only. Load `sdk-server/AGENTS.md` for DSL and rendering details.
Do NOT load `sdk-android` or `app-template` unless cross-cutting.

## Key files

| File | Purpose |
|---|---|
| `Main.kt` | `main()` reads env (`PORT`, `COMPOSECAST_DATA_DIR`, `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS` → `HttpImageLoader(allowPrivateHosts)`, T-026; `COMPOSECAST_RELOAD`, T-042; `COMPOSECAST_MODE=files|admin`, T-007; admin mode: `COMPOSECAST_ADMIN_EMAIL`/`_PASSWORD`, `COMPOSECAST_ADMIN_ALLOW_HTTP`; the wizard's API probe follows `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS`; admin mode refuses a non-writable data dir with the `chown` fix), builds the registry **before** starting (fail fast); `Application.module(LiveScreens, AdminPanelConfig?)` (`AdminPanelConfig(auth, screens, settings, publishing, allowHttp, allowPrivateApiHosts)`) (admin mode mounts `server-admin`'s `adminPanel`): CallLogging, ContentNegotiation, StatusPages (`ScreenParameterException` → 400, `DataSourceException` → 502, other → logged JSON 500) |
| `Routing.kt` | All HTTP endpoints (screen id = `RoutingCall.pathParameters`, never `parameters`, which also holds the query: a screen param `id` used to replace it — fixed in T-031); `configureRouting(screens: LiveScreens)`; ETag / `If-None-Match`; renders in `Dispatchers.IO` (bound screens block on data sources) |
| `AdminScreens.kt` | Admin mode (T-007 A1): opens `compose-cast.db` via `server-store`, imports the data dir on the first start (validated with `loadScreens`, one transaction), builds the registry from live rows + stored brand; `openDatabase`; `bootstrapFromEnv` (first admin, required while there is no account); A4/A5: `publish`/`rollback`/`publishBrand`/`setHome` build the registry with the change **before** writing (one lock: build → write → `serve`), `AdminPublishing` (`ScreenPublishing` → `LiveScreens.replace` / `replaceHome`); `preview(json, id)` = a one-screen registry with the live brand, nothing written |
| `LiveRoute.kt` | T-047: `installLiveScreens()` (Ktor WebSockets, ping 15 s) and `liveScreens(screens)` — one `LiveFrames` per connection, rendered in `Dispatchers.IO`, registry looked up every tick (publish/reload reach open sockets); id from the request path like GET. Follow-up: share one render loop per `(screen, params)` between viewers |
| `ConnectPage.kt` | `GET /connect` (T-045, ADR 0026): page for the admin's QR code; `intent://` link to the Preview app (`PREVIEW_PACKAGE`) with the APK as fallback; own address from `X-Forwarded-Proto` + validated `Host` |
| `DataDirReloader.kt` | Polls `COMPOSECAST_DATA_DIR` and swaps reloaded screens into `LiveScreens` (T-042); `reloadIfChanged()` is the testable step |
| `LiveScreens.kt` | `AtomicReference` holder of the served `ScreenRegistry`; `replace()` swaps it atomically (admin publish, reloads); `registryFor(id)`: `__preview-…` ids from `previews` |
| `PreviewScreens.kt` | T-045 B2 (ADR 0027): in-memory draft previews, `newId()` = `__preview-` + 128 random bits, 30 min TTL, max 100 |
| `Screens.kt` | `loadScreens(dataDir)` (JSON data dir or demo), `demoScreens()` (registers the demo screens in order), `demoBrand`; injectable `ImageLoader`/`DataLoader` for tests |
| `demo/Demo<Id>Screen.kt` | One file per demo screen: `internal fun RegistryBuilder.<id>Screen()` (incl. dynamic `echo`, bound `data` on Open-Meteo, `dashboard` with gauges/kpis/sparkline — T-047, dynamic `live`: the same components simulated from the clock, `live(everyMs = 1000)`); `DemoShared.kt`: `featureCard`, `HERO_IMAGE`, `SURFACE` |
| `src/main/resources/logback.xml` | SLF4J provider config — without logback the server logged nothing (not even 500 stack traces) |
| `src/test/.../RoutingTest.kt` | Ktor `testApplication`: 304, query params (non-ASCII), 400/404, 50 concurrent requests, registry swap |
| `src/test/.../LiveRouteTest.kt`, `LiveDemoTest.kt` | WebSocket frames (full → data updates, nothing when unchanged, close reasons), `X-Composecast-Live`; the `live` demo sends only data updates for 30 s |
| `src/test/.../JsonScreensTest.kt` | `examples/demo` == DSL definitions and brand, canonical, byte-identical RC documents |

`examples/demo/` (repo root) is a complete data directory: `screens/*.json` + `brand.json`, a JSON
copy of `demoScreens()`. `./gradlew :server-app:run` runs from the repo root, so relative
`COMPOSECAST_DATA_DIR` paths work.

## Docker (T-009)

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage: `gradle:9.6.0-jdk21-noble` (Gradle preinstalled — **not** the wrapper, whose download from GitHub Releases failed with 504 on 2026-11-08; version must equal the wrapper's, checked by `verify-docker.sh`) runs `installDist` with `-Pcomposecast.serverOnly=true` (no Android SDK) on `$BUILDPLATFORM` → `eclipse-temurin:21.0.13_9-jre-alpine`, non-root `composecast` (UID/GID 1000, owns `/data`: admin mode writes `compose-cast.db`), `COMPOSECAST_DATA_DIR=/data` (no demo fallback), healthcheck = busybox `wget /health` |
| `/docker-compose.yml` (root) | Dev/demo: builds the image, mounts `${COMPOSECAST_SCREENS:-./examples/demo}:/data:ro` (files mode; admin mode needs a writable copy), optional `.env` |
| `/.dockerignore` (root) | Context = repo root; excludes Android modules, docs, build output, PNGs |
| `/scripts/verify-docker.sh` | `verify.sh --docker`: build, healthy < 30 s, `/screens`, admin mode on a named volume (login) + read-only `/data` refused, size < 275 MB (bloat guard; see the script); then installs `distribution/docker-compose.yml` into an empty dir against the built image (retagged `ghcr.io/…:verify`) + `config` of the `https` profile |
| `/distribution/docker-compose.yml`, `/distribution/example.env` | Production install (T-023): pulls `ghcr.io/ljarka/compose-cast-server:${COMPOSECAST_VERSION:-release}`, data dir writable (admin mode; `chown -R 1000:1000 data`), whole `.env` → container (secrets), profile `https` = Caddy `reverse-proxy`; no fixed project/container names (several apps per host). Playbook: `docs/playbooks/install-server.md` |
| `/.github/workflows/release.yml` | Push to `main` → `:main`; published release → `:<tag>` + `:release` + the two distribution files as release assets; buildx amd64 + arm64 (QEMU for the runtime stage), OCI labels via `metadata-action` |

- The build stage `COPY`s every module `server-app` depends on (transitively); a new one is
  enforced by `check-architecture.sh` R11 (A1 forgot `server-store` and only Docker failed).
- The build stage must `chmod -R a+rX` the install: Gradle-cache jars are 0600 → `NoClassDefFoundError` as non-root.
- Pin base image tags (`21.0.13_9-…`); bump them with the JDK. Apps pull the published image (T-023, ADR 0017).

## Endpoints

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `200 ok` (plain text) |
| GET | `/screens/__preview-…` | A draft preview (admin mode, 30 min, ADR 0027); 404 when unknown/expired |
| GET | `/screens/{id}?k=v` | RC binary (`application/octet-stream`); query = `RenderContext.params`; headers `X-Composecast-Screen-Title`, `X-Composecast-Live` (live screens: `everyMs`, also on 304), `ETag`, `Cache-Control: no-cache`; `If-None-Match` match → `304`; missing required param → `400`; failed data source without `error` components → `502` |
| WS | `/screens/{id}/live?k=v` | Live screens only (T-047, [ADR 0029](../docs/decisions/0029-live-screens-websocket-data-updates.md)): binary frames = RC documents; first full, then only on change — data-update document (`DOC_DATA_UPDATE`) or full when the structure changed; every `live.everyMs`; pings every 15 s. Not live / unknown / missing param / too old → close `CANNOT_ACCEPT` + reason. A failed data source after the first frame skips the tick |
| GET | `/screens/{id}/debug?k=v` | Plain-text decoded RC tree + etag (dev/debug only) |
| GET | `/screens` | JSON list of screen ids (static and dynamic) |
| GET | `/brand` | JSON `BrandConfig` |
| GET | `/connect?screen=` | HTML: open this server (and optionally a screen, e.g. a draft preview) in the compose-cast Preview app (T-045); 400 for a malformed `Host` or `screen` |
| GET/POST | `/admin/**` | Admin mode only: the admin panel ([server-admin](../server-admin/AGENTS.md)) |

## Rules

- `Routing.kt` gets the registry as a parameter (testable with Ktor `testApplication { application { module(registry) } }`);
  it never imports `sdk-server` DSL types directly.
- **Changing a demo screen = change `examples/demo` too**; `JsonScreensTest` fails and prints the
  expected file.
- Routing reads `LiveScreens.current` per request. `DataDirReloader` (T-042) polls the data dir
  every 2 s (files + mtimes + sizes; inotify is unreliable on Docker bind mounts), waits 500 ms,
  builds a new registry and `replace()`s it; a failing load is logged once and the old registry
  stays. `COMPOSECAST_RELOAD=false` turns it off. Admin publish (T-007 A4) `replace()`s the same way, after building the new registry.
- `examples/demo` has no JSON for `echo`: it stays the DSL `dynamicScreen` example; JSON screens
  get request params through `params` + `{{params.x}}` (T-006, see `data.json`).
- The demo `data` screen calls the live Open-Meteo API (keyless); tests and screenshots use a fake
  `DataLoader` with fixture JSON — no network in CI.
- `ImageLoader` is injected (default: `HttpImageLoader()`, private hosts blocked unless
  `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS=true`) so `compat-tests` can use a fake one
  without network access.
- New endpoints always added in `Routing.kt`; no business logic in `Routing.kt` itself.

## Dependencies

- `:sdk-server` (api) — `composeCast {}`, `ScreenRegistry`, `ImageLoader`
- `:protocol` (api) — `Headers`, `BrandConfig`
- `:server-store`, `:server-admin` — admin mode (T-007)
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
