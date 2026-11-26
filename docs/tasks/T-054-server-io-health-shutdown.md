# T-054 — Server I/O offloading, database-aware `/health`, and graceful shutdown

Status: done · Roadmap: quality · Depends on: — · Module: server-admin, server-app

## Goal

Keep blocking I/O off Ktor's event-loop threads in `server-admin`, make `/health` reflect database
readiness in admin mode, and cleanly close background resources on server shutdown.

## Why

- `PhonePreview.kt` performs synchronous `HttpClient.send` (20 s timeout) and `ApiProbe.kt`
  performs synchronous external HTTP calls without `withContext(Dispatchers.IO)`.
- `GET /health` always returns `"ok"` even if the admin SQLite database is unreachable.
- `DataDirReloader` is started in `Main.kt` without a shutdown hook on `ApplicationStopping`.

## Design

- Wrap blocking `renderer.png(...)` / `publishing.document(...)` calls in `PhonePreview.kt` and
  `probe.probe(...)` calls in `WizardPages.kt` / `VisualPages.kt` with `withContext(Dispatchers.IO)`.
- Allow `/health` to run an optional readiness check (in admin mode: lightweight DB ping) and return
  503 when unhealthy.
- Register `ApplicationStopping` listener in `Main.kt` to stop `DataDirReloader` cleanly.

## Steps (files to touch)

1. `server-admin/.../PhonePreview.kt`, `WizardPages.kt`, `VisualPages.kt`: offload blocking HTTP
   calls to `Dispatchers.IO`.
2. `server-app/.../Routing.kt`, `Main.kt`: DB readiness check in `/health` + `ApplicationStopping`
   hook for `DataDirReloader`.
3. Tests in `server-app` for `/health` healthy & unhealthy states.

## Acceptance criteria

- Blocking HTTP calls in `server-admin` run on `Dispatchers.IO`.
- `/health` returns 200 when healthy and 503 when the readiness check fails.
- `DataDirReloader` stops on `ApplicationStopping`.
- `./scripts/verify.sh` is green.

## Verification

`./scripts/verify.sh`

## Docs to update

`server-admin/AGENTS.md`, `server-app/AGENTS.md`, `docs/history.md`.

## Out of scope

Prometheus / Micrometer metrics (T-056).
