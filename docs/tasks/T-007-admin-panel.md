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

## Review notes (2026-11-07) — reconcile before detailing the steps

ADRs 0011–0014 were written before T-002 existed. Against the implemented model:

1. **File content.** ADR 0011 says `<id>.json` holds "components, metadata, draft/live status",
   but ADR 0013 keeps drafts in memory only, ADR 0014 says the current file *is* the live version,
   and `ScreenJson` is strict (extra keys are errors). Proposal: `<id>.json` = exactly a
   `ScreenDefinition` — the same format as `examples/demo/screens`, so a hand-edited data dir and
   an admin-edited one are the same thing and `COMPOSECAST_DATA_DIR` already serves it. Publish
   metadata (who/when) goes into the history snapshot name or a sidecar file, not into `<id>.json`.
2. **One process.** "Backend easy to start" and the `/screens/__preview` URL on the public port
   both point to the admin being a module *mounted into* `server-app` (same port, same registry),
   not a separate deployable (resolves step 7). Requires T-003's swappable registry.
3. **Snapshot names** (ADR 0014): `2026-11-07T14:32:00Z.json` has `:` (invalid on Windows, needs
   escaping in the rollback URL) and collides for two publishes in one second → use
   `20261107T143200Z[-n].json`.
4. **Preview on device** (ADR 0013) needs the app to open a given screen: `app-template` has no
   deep links / QR handling yet → add an app step (e.g. `composecast://screen/__preview` intent
   filter). The ADR's curl example posts `{"components":[]}` without the required `id`.
5. **Validation UX:** publish runs `ScreenDefinition.problems()` and shows the
   `components[i].field: …` messages next to the editor; nothing invalid reaches the registry.
6. **Security:** admin-entered image URLs are fetched by the server (SSRF, huge downloads) —
   T-003 adds scheme + size limits; consider blocking private address ranges.

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
- **Stateful / interactive components** (expand/collapse, toggle, counter) — the RC state API is
  available in the procedural DSL (verified 2026-11-07, see T-008); first version of the admin
  panel supports static documents only; add stateful components after the T-008 PoC.
