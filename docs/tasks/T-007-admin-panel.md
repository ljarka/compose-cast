# T-007 — Admin panel (Etap 8)

Status: needs design detail · Roadmap: Etap 8 · Depends on: T-002 (JSON screen model)

## Goal

A web UI served by the Ktor backend where a non-developer can create and edit screens,
preview them, and publish them live — without touching Kotlin code.

## Why

See [README → Etap 8](../../README.md#etap-8--admin-panel-mvp). Users (content editors,
designers) need to change UI without a code deploy; the Kotlin DSL is too high a barrier.

## Decisions needed before starting (write ADRs first)

- **Storage format**: JSON files on disk (git-friendly, easy backup, one file per screen) vs
  embedded SQLite (Exposed). Decision needed → write ADR 0011.
- **Admin web UI technology**: server-rendered HTML (Freemarker/Thymeleaf) vs static
  HTML+HTMX vs a separate SPA (React/Vite in a separate module) → write ADR 0012.
- **Preview in the browser**: TypeScript RC player (`camaelon/remotecompose-experiments`) vs
  server-rendered PNG vs "preview on device" only via WebSocket push → write ADR 0013.
- **Change history & rollback**: git-based (commit each publish) vs append-only JSON log →
  write ADR 0014.

## High-level design (to be detailed after ADRs)

```
Admin UI (browser)
  POST/PUT/DELETE /admin/api/screens/{id}  ← REST CRUD on screen definition (JSON)
  POST /admin/api/screens/{id}/preview     ← returns .rc binary for browser/device preview
  GET  /admin/api/screens                  ← list (with draft/live status, last published)

Storage (TBD by ADR 0011)
  → RemoteComposeWriter (via sdk-server) → cached .rc bytes → GET /screens/{id}

Auth: HTTP Basic, password from ADMIN_PASSWORD env var
```

## Steps (to be filled after ADRs)

1. Write ADRs 0011–0014 (storage, UI tech, preview, change history).
2. Create `admin-server` module; wire into `settings.gradle.kts`.
3. Implement REST CRUD endpoints with chosen storage.
4. Implement basic HTML UI with component palette and property editor.
5. Implement preview endpoint.
6. Implement change history + rollback.
7. Add `admin-server` to `server-app` or as a separate deployable (decide in ADR 0011/0012).
8. Screenshot-test the preview output; add to `compat-tests`.

## Acceptance criteria

- A new screen created in the admin panel appears on `GET /screens/{id}` as a valid `.rc` document.
- An existing screen can be edited and published; previous version can be rolled back.
- Preview shows the screen (browser or device) before publishing.
- `POST /admin/api/screens` without `ADMIN_PASSWORD` returns 401.
- `./scripts/verify.sh` green.

## Verification

```bash
curl -u admin:$ADMIN_PASSWORD -X POST http://localhost:8080/admin/api/screens \
  -H 'Content-Type: application/json' \
  -d '{"id":"test","title":"Test","components":[]}'
curl http://localhost:8080/screens/test | xxd | head   # should be .rc binary
```

## Docs to update

- `docs/decisions/` — ADRs 0011–0014 (see above)
- `docs/history.md` — entry for this task
- `docs/tasks/README.md` — mark done, specify T-008
- `AGENTS.md` — add `admin-server` to module map
- `README.md` — tick Etap 8 checkboxes

## Out of scope

- Authentication beyond HTTP Basic (OAuth, SSO)
- Multi-user / roles (editor vs publisher) — later enhancement
- Experiment/A/B variant management (Etap 12)
- **Stateful / interactive components** (expand/collapse, toggle, counter) — blocked on T-008
  spike; first version of admin panel supports static documents only. Add after T-008 confirms
  state API availability in procedural RC.
