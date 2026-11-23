# T-007 — Admin panel MVP (roadmap A1–A5)

Status: done (A1–A5) · Roadmap: [specs/roadmap.md A1–A5](../../specs/roadmap.md#a-admin-panel-mvp-t-007-split--the-smallest-vertical-slice), Etap 8 · Depends on: T-002 (JSON screen model), T-006 (data sources), T-042 (`LiveScreens` swap) · Module: server-store, server-admin, server-app

## Goal

A web UI on the owner's server where screens are listed, edited (JSON for now), validated,
published live and rolled back, and the app's settings are kept — the first vertical slice toward
"a non-developer ships an app" ([mission](../../specs/mission.md)). The visual editor is T-029;
preview on a phone is T-045.

## Decisions (all accepted)

| Area | Chosen |
|---|---|
| Modules | `server-store` (`io.composecast.server.store`: database, migrations, repositories) and `server-admin` (`io.composecast.server.admin`: routes, templates), each with an `AGENTS.md` (ADR 0023); `server-app` only wires them. Versions in `gradle/libs.versions.toml` |
| Storage | SQLite via Exposed, `$COMPOSECAST_DATA_DIR/compose-cast.db`; a screen row stores exactly the canonical `<id>.json` text (ADR 0016) |
| Modes | *files* (no database: today, read-only, T-042 reload) and *admin* (database is the source of truth). First start in admin mode imports `screens/*.json` + `brand.json` (ADR 0016) |
| Serving | Every change builds a new registry and `LiveScreens.replace()`s it (as T-042) |
| Auth | Email + bcrypt, session cookie, roles `admin`/`editor`/`viewer`; first admin from `COMPOSECAST_ADMIN_EMAIL` / `COMPOSECAST_ADMIN_PASSWORD` (ADR 0016) |
| UI | Freemarker + HTMX, no npm (ADR 0012); mounted at `/admin` on the same port |
| Validation | `ScreenDefinition.problems()` — the same code as file loading; nothing invalid reaches `LiveScreens` |
| History | Append-only `screen_history` rows, rollback = publish of an old version (ADR 0014 semantics, ADR 0016) |

## Database schema (A1, A2, A5)

```sql
CREATE TABLE screens (id TEXT PRIMARY KEY, draft_json TEXT, live_json TEXT,
  updated_at TEXT NOT NULL, published_at TEXT, published_by TEXT);   -- NULL draft = no pending draft
CREATE TABLE screen_history (id INTEGER PRIMARY KEY AUTOINCREMENT, screen_id TEXT NOT NULL,
  json_text TEXT NOT NULL, published_at TEXT NOT NULL, published_by TEXT NOT NULL);
CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT NOT NULL UNIQUE,
  hash TEXT NOT NULL, role TEXT NOT NULL);                            -- admin | editor | viewer
CREATE TABLE sessions (id TEXT PRIMARY KEY, user_id INTEGER NOT NULL, expires_at TEXT NOT NULL);
CREATE TABLE app_settings (key TEXT PRIMARY KEY, value TEXT);          -- brand.json, app name, logo (A5)
```

## Steps (one per session; each ends with `verify.sh` green and a commit)

### A1 — `server-store` + admin mode
- New module `server-store`: Exposed + sqlite-jdbc, schema migration on start, `ScreenStore`
  (list, get, saveDraft, publish, history, rollback) — `screens` and `screen_history` only.
- `server-app`: `COMPOSECAST_MODE=files|admin` (default `files`, unchanged). Admin mode: open the
  database, import `screens/*.json` + `brand.json` when empty, build the registry from live rows,
  serve through `LiveScreens`; the T-042 reloader runs only in files mode.
- **Done when:** a test imports `examples/demo` into a temp database and the served documents are
  byte-identical to files mode; restart keeps the data; files mode tests unchanged.

**A1 result (2026-11-23):** `server-store` (`StoreDatabase`, `ScreenStore`, `SettingsStore`;
Exposed 1.6.1, sqlite-jdbc 3.54.1.0), `server-app/AdminScreens.kt`, `COMPOSECAST_MODE`. Tests:
`ScreenStoreTest`, `AdminScreensTest` (all `examples/demo` screens byte-identical to files mode;
a restart never re-imports). Open for A2: the Docker data volume is mounted `:ro` in both
compose files — admin mode needs it writable (and owned by the image's `composecast` user);
change it together with the install docs when the admin is usable.

### A2 — Login and roles
- `users` + `sessions` tables; bcrypt; first admin from env (only when `users` is empty);
  `server-admin` module with `/admin/login`, logout, an empty `/admin` page; Ktor sessions.
- **Done when:** `/admin` redirects to login without a session; the env admin logs in; wrong
  password → error; a `viewer` gets 403 on every mutating route (tests with the Ktor test host).

**A2 result (2026-11-23, `e3e07ef`):** module `server-admin` (`AdminPanel.kt`, `AdminAuth.kt`, `Role.kt`,
Freemarker templates), `UserStore` in `server-store`, `COMPOSECAST_ADMIN_EMAIL`/`_PASSWORD`/
`_ALLOW_HTTP` in `server-app`. Tests: `AdminPanelTest` (8), `UserStoreTest` (3), `AdminScreensTest`
(+2: bootstrap from env, `/admin` only in admin mode); checked by hand with curl against a real
admin-mode server. Session/cookie/password decisions: [server-admin/AGENTS.md](../../server-admin/AGENTS.md).
Notes for A3: add pages inside `loggedIn { }` in `adminRoutes` (the role policy and its test cover
them); HTMX is not loaded yet (pin the unpkg URL + integrity hash, ADR 0012); the `:ro` data volume
(A1 note) is still open.

### A3 — Screen list + JSON editor + drafts
- `/admin/screens` (id, title, status live/draft/unpublished, last published);
  `/admin/screens/{id}` editor: textarea + HTMX validation on input (`problems()` with JSON paths),
  "Save draft"; create a new screen.
- **Done when:** an invalid edit shows the errors and cannot be saved as publishable; a saved draft
  does not change `GET /screens/{id}` (test + manual check in the browser).

**A3 result (2026-11-23, `4cad7b9`):** `server-admin/ScreenPages.kt` + `screens.ftl`, `screen.ftl`,
`_problems.ftl`; `ScreenJson.check` in `protocol`. Invalid edits are not saved at all (422, text
kept); valid drafts are stored canonical. Tests: `ScreenPagesTest` (7), `ScreenJsonTest` (+1),
`AdminScreensTest` (+1: a draft saved through `/admin` leaves `GET /screens/main` byte-identical).
Checked with curl against a real admin-mode server (list, validate, save, create). **Not done: the
browser check** — no browser tool was available to the agent; open `/admin/screens/main` on
`localhost`, type invalid JSON and see the problems appear (HTMX) before relying on it.
Notes for A4: publish = validate the draft again, `ScreenStore.publish`, rebuild the registry
(`AdminScreens.load()`) and `LiveScreens.replace()`; `server-admin` cannot see `LiveScreens`
(R10), so pass a callback (e.g. `onPublished: () -> Unit`) through `adminPanel` from `server-app`.
The `:ro` data volume (A1 note) is still open.

### A4 — Publish, history, rollback
- "Publish" validates, writes live + a history row, rebuilds the registry and `replace()`s it;
  history list per screen (who, when); "Roll back" publishes an old version.
- **Done when:** after publish, `GET /screens/{id}` serves the new document without a restart; a
  rollback restores the previous bytes; history is append-only (tests).

**A4 result (2026-11-23, `ad4591f`):** `server-admin/ScreenPublishing.kt` (interface) + publish/rollback/revision
routes, `AdminScreens.publish`/`rollback` + `AdminPublishing` in `server-app`, `ScreenStore.revision`
and draft-keeping rollback. Tests: `AdminScreensTest` (+3 over HTTP: publish serves the new
document, rollback restores the imported bytes, a screen with an unset secret is refused and
nothing changes, a new screen appears for apps once published), `ScreenPagesTest` (+3),
`ScreenStoreTest` (rollback keeps the draft). Real admin-mode server checked with curl: publish →
new title without restart; rollback; restart keeps it. (The demo `main` embeds
`picsum.photos`, a random image, so its bytes change on every rebuild in any mode — not a bug.)
Notes for A5: a brand change must rebuild the registry the same way (build → write → serve,
same lock: add `AdminScreens.saveBrand(..., serve)` and extend `ScreenPublishing` or add a
`SettingsPublishing`); every publish re-renders all static screens and re-fetches their images
(fine for tens of screens; cache images if it gets slow). Still open: the `:ro` data volume
(A1 note) and install docs (`COMPOSECAST_MODE=admin`, admin env) — the admin is now usable, so do
them with A5 or right after.

### A5 — App Settings
- `/admin/settings`: app name, applicationId, server URL, brand colors (BrandConfig incl. T-040
  surface/text/outline), logo upload (stored for T-010); saving rebuilds the registry.
- **Done when:** a brand color change is visible in `/brand` and in the rendered screens; invalid
  colors are rejected with the field named.

**A5 result (2026-11-23, `6ba75e7`):** `server-admin/SettingsPages.kt` + `settings.ftl`, `BrandConfig.problems()`,
`AdminScreens.publishBrand` (build → write → serve, same lock). Docker: UID 1000 owning `/data`,
writable production volume, fail-fast on a read-only data dir, install playbook + `example.env`,
admin smoke test in `verify-docker.sh`. Tests: `SettingsPagesTest` (4), `BrandConfigTest`,
`AdminScreensTest` (+1: a color change is in `/brand` and re-renders `main`; `pink` is refused
naming `secondaryColor`). Not checked: the new colors on the emulator (renderer unchanged; brand
rendering is covered by `compat-tests`) and the pages in a browser (no browser tool) — do both
when T-045 puts the admin and the app side by side.

## Verification (every step)

`./scripts/verify.sh`; manual in Docker: `COMPOSECAST_MODE=admin`, log in, edit and publish a
weather screen, the app on the emulator shows it (rule 5 — from A4 on).

## Docs to update (per step)

New module `AGENTS.md` files (A1 `server-store`, A2 `server-admin`), root `AGENTS.md` module map,
`server-app/AGENTS.md`, `docs/playbooks/install-server.md` (env: `COMPOSECAST_MODE`, admin
credentials), `distribution/example.env`, `docs/feature-checklist.md` (Etap 8 boxes), history.

## Out of scope

Preview on a phone (T-045), visual editor (T-029), OAuth/SSO, multi-tenancy (never: one server =
one product), A/B flags (T-038), deleting users from the UI (env/CLI is enough for the MVP).
