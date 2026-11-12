# T-007 — Admin panel (Etap 8)

Status: ready · Roadmap: Etap 8 · Depends on: T-002 (JSON screen model), T-006 (data sources)

## Goal

A web UI served by the Ktor backend where a non-developer can create and edit screens,
preview them, and publish them live — without touching Kotlin code.

## Why

See [README → Etap 8](../../README.md#etap-8--admin-panel-mvp). Users (content editors,
designers) need to change UI without a code deploy; the Kotlin DSL is too high a barrier.

## Architecture decisions (all decided)

| Decision | Chosen |
|---|---|
| Storage | SQLite (Exposed) in `$COMPOSECAST_DATA_DIR/compose-cast.db` — ADR 0016 |
| Auth | Email + bcrypt, session cookie, roles `admin`/`editor`/`viewer` — ADR 0016 |
| UI tech | Freemarker (server-rendered HTML) + HTMX for partial updates — ADR 0012 |
| Preview | Server-rendered PNG from RC binary (browser shows image) — ADR 0013 |
| History | `screen_history` table in SQLite; timestamp key `20261107T143200Z[-n]` — ADR 0014/0016 |
| Deployment | Admin mounted into `server-app` on the same port at `/admin/*` (not a separate module) |

## Review notes resolved

1. ~~File content~~ → SQLite (ADR 0016); `ScreenJson` text column = canonical `ScreenDefinition` JSON
2. ~~Separate process~~ → admin is a Ktor `Route` mounted into `server-app`; same JVM, same registry
3. ~~Snapshot names with `:`~~ → `screen_history` rows use timestamp `20261107T143200Z[-n]` (no colons)
4. **Preview on device** — `app-template` gets intent filter `composecast://screen/__preview`; admin
   shows a QR code linking to `composecast://screen/__preview?server=<base>&id=<id>`
5. **Validation UX** — `POST /admin/api/screens/{id}/publish` calls `ScreenDefinition.problems()`
   and returns `{"errors": ["components[0].text: empty text"]}` with 422 before saving; nothing
   invalid reaches the live registry
6. **SSRF** — `HttpDataLoader` and `HttpImageLoader` reject URLs resolving to private address
   ranges (RFC 1918 + loopback + link-local); scheme must be `https` for non-localhost; response
   size capped at 2 MB (already specified in T-006 for data sources; apply same guard to image URLs)

## High-level design

```
Browser ──► /admin/login  (session cookie)
        ──► /admin/screens  (screen list; Freemarker)
        ──► /admin/screens/{id}/edit  (editor; Freemarker + HTMX)
        ──► /admin/api/screens  (CRUD JSON REST; HTMX calls)
        ──► /admin/api/screens/{id}/preview  → PNG (server-renders .rc → PNG via RemoteCompose)
        ──► /admin/api/screens/{id}/publish  → 204 / 422
        ──► /admin/api/screens/{id}/history  → list of revisions
        ──► /admin/api/screens/{id}/history/{ts}/rollback  → 204

Ktor Registry ──► /screens/{id}  (unchanged public endpoint, serves live .rc)
```

## Database schema

```sql
CREATE TABLE users (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  email   TEXT NOT NULL UNIQUE,
  hash    TEXT NOT NULL,  -- bcrypt
  role    TEXT NOT NULL DEFAULT 'editor'  -- admin | editor | viewer
);

CREATE TABLE screens (
  id          TEXT PRIMARY KEY,
  title       TEXT NOT NULL,
  draft_json  TEXT,         -- nullable: NULL = no pending draft
  live_json   TEXT,         -- nullable: NULL = not published yet
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL,
  published_at TEXT,
  published_by TEXT         -- user email
);

CREATE TABLE screen_history (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  screen_id   TEXT NOT NULL,
  ts          TEXT NOT NULL,  -- 20261107T143200Z[-n]
  json_text   TEXT NOT NULL,
  published_by TEXT NOT NULL,
  UNIQUE(screen_id, ts)
);

CREATE TABLE app_settings (
  key   TEXT PRIMARY KEY,
  value TEXT
);
```

## Steps

### Step 1 — Database + auth layer
Files to create:
- `server-app/src/main/kotlin/cc/composecast/server/admin/db/Database.kt`
  — `DatabaseFactory.init(dataDir)`, Exposed table objects, `Migrations.run()`
- `server-app/src/main/kotlin/cc/composecast/server/admin/db/Users.kt`
  — CRUD: `createUser`, `findByEmail`, `verifyPassword`, bootstrap first admin
- `server-app/src/main/kotlin/cc/composecast/server/admin/db/Screens.kt`
  — CRUD: `listScreens`, `getDraft`, `getLive`, `saveDraft`, `publish`, `rollback`, `listHistory`
- `server-app/src/main/kotlin/cc/composecast/server/admin/auth/AdminSession.kt`
  — `data class AdminSession(email, role)`, `SessionStorage`

Add to `server-app/build.gradle.kts`:
```kotlin
implementation("org.jetbrains.exposed:exposed-core:0.56.0")
implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
implementation("org.xerial:sqlite-jdbc:3.47.0.0")
implementation("at.favre.lib:bcrypt:0.10.2")
implementation("io.ktor:ktor-server-sessions:$ktor_version")
```

Bootstrap: on startup, if `users` table is empty and `COMPOSECAST_ADMIN_EMAIL` +
`COMPOSECAST_ADMIN_PASSWORD` are set → insert first admin.

### Step 2 — Freemarker + HTMX setup
Add `io.ktor:ktor-server-freemarker` to `server-app/build.gradle.kts`.
Create `server-app/src/main/resources/templates/`:
- `layout.ftl` — base HTML (Bootstrap 5 CDN, HTMX CDN, navbar, session user display)
- `login.ftl` — email + password form
- `screens-list.ftl` — table: id, title, status (draft/live/unpublished), last published, actions
- `screen-editor.ftl` — split pane: JSON `<textarea>` left, preview `<img>` right; "Save draft" / "Publish" / "Rollback" buttons

HTMX endpoints return partial HTML fragments (`hx-target`, `hx-swap`).

### Step 3 — REST CRUD API (`/admin/api/*`)
Routes (all require auth; `editor` role for mutations, `viewer` for GET):
- `GET  /admin/api/screens` → `[{id, title, status, publishedAt}]`
- `GET  /admin/api/screens/{id}` → `{id, draftJson, liveJson, ...}`
- `POST /admin/api/screens` → create with `{id, json}`, validate, save as draft; 400 on invalid id
- `PUT  /admin/api/screens/{id}/draft` → save draft JSON; 422 on `problems()` errors
- `POST /admin/api/screens/{id}/publish` → validate → save live + history row; 422 on errors
- `DELETE /admin/api/screens/{id}` → remove draft + live; admin only; 403 otherwise
- `GET  /admin/api/screens/{id}/history` → `[{ts, publishedBy, publishedAt}]`
- `POST /admin/api/screens/{id}/history/{ts}/rollback` → copy history row to live; editor+
- `POST /admin/api/screens/{id}/preview` → render live (or draft) JSON → PNG; 400 on parse error

Publish also updates the in-memory `ScreenRegistry` via a new `ScreenRegistry.putLive(id, definition)` method so `GET /screens/{id}` starts serving the new content without a restart.

### Step 4 — Preview endpoint
`POST /admin/api/screens/{id}/preview`:
1. Parse `ScreenDefinition` from stored draft JSON (or from request body for unsaved drafts).
2. Call `RemoteComposeRenderer.render(definition)` → `.rc` binary.
3. Use `RemoteCompose` desktop renderer (or a headless AWT canvas via `compat-tests` dependency)
   to rasterize the `.rc` to a 360×640 PNG.
4. Return `Content-Type: image/png`.

If a desktop renderer is not available in `server-app`, return the raw `.rc` binary with
`Content-Type: application/octet-stream` and add a "Preview on device" QR code path instead
(show QR in the editor UI pointing to `composecast://screen/__preview?server=…&id=…`).

### Step 5 — Preview on device (app-shell)
(T-024: the app code is `app-shell`; `app-template` has no sources.)
In `app-shell/src/main/AndroidManifest.xml`, add intent filter to `ComposeCastActivity`:
```xml
<intent-filter android:autoVerify="false">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="composecast" android:host="screen" />
</intent-filter>
```
In `ComposeCastActivity`, handle `intent.data.path == "/__preview"`: read `server` + `id` query params,
load `GET {server}/screens/{id}` and render with `RemoteComposePlayer`.

### Step 6 — SSRF protection
In `sdk-server/HttpDataLoader.kt` and `sdk-server/HttpImageLoader.kt`, add before any outbound
connection:
```kotlin
fun InetAddress.isPrivate(): Boolean =
    isSiteLocalAddress || isLoopbackAddress || isLinkLocalAddress ||
    isAnyLocalAddress || hostAddress.startsWith("169.254") ||
    hostAddress.startsWith("100.6") // WARP mesh — blocked in admin-entered URLs
```
Resolve the hostname first, then check. Scheme must be `https` (not `http`) for non-loopback
in production. Fail with `DataSourceException` / `ImageLoadException` and log at WARN.
This guard applies to admin-entered image URLs and data source URLs; it does NOT apply to the
demo Open-Meteo URL (literal host, not admin-entered).

### Step 7 — HTML UI routes
Mount under `server-app/Routing.kt`:
```kotlin
authenticate("admin-session") {
    route("/admin") {
        adminRoutes()   // Freemarker pages
        route("/api") { adminApiRoutes() }   // JSON API
    }
}
route("/admin/login") { loginRoutes() }
```

### Step 8 — Tests
- `AdminApiTest` (JVM, TestEngine): login → create screen → save draft → publish → verify
  `GET /screens/{id}` returns `.rc` binary; rollback restores previous live.
- `AdminAuthTest`: unauthenticated → 401; viewer role → 403 on mutation.
- Existing `CompatTests` golden screenshots must not change.

### Step 9 — Docs
- `AGENTS.md` root: add admin module description to module map
- `docs/history.md`: entry for T-007
- `README.md`: tick Etap 8 checkboxes
- `docs/decisions/0011-admin-storage.md` through `0014-admin-history.md`: update to reflect SQLite
  decision (ADR 0016 superseded them; add a note at the top of each pointing to 0016)

## Acceptance criteria

- [ ] `POST /admin/api/screens` (authed) creates a screen; `GET /screens/{id}` returns `.rc` binary
- [ ] `PUT /admin/api/screens/{id}/draft` with invalid JSON returns 422 with field-level errors
- [ ] `POST /admin/api/screens/{id}/publish` updates the live registry without restart
- [ ] `GET /admin/api/screens/{id}/history/{ts}/rollback` restores a previous version
- [ ] Unauthenticated request to `/admin/api/screens` returns 401
- [ ] `viewer` role gets 403 on `PUT`/`POST`/`DELETE`
- [ ] Admin-entered data source URL pointing to `127.0.0.1` is rejected at save time
- [ ] Preview returns a PNG (or `.rc` binary if PNG renderer unavailable) with 200
- [ ] `app-template` opens `composecast://screen/__preview?server=…&id=…` and renders the screen
- [ ] `./scripts/verify.sh` green (existing goldens unchanged)

## Verification

```bash
# Start the server
docker compose up -d

# Log in
curl -c cookies.txt -X POST http://localhost:8080/admin/api/login \
  -d '{"email":"admin@example.com","password":"secret"}' -H 'Content-Type: application/json'

# Create and publish a screen
curl -b cookies.txt -X POST http://localhost:8080/admin/api/screens \
  -H 'Content-Type: application/json' \
  -d '{"id":"hello","json":"{\"id\":\"hello\",\"title\":\"Hello\",\"components\":[{\"type\":\"text\",\"text\":\"Hi\"}]}"}'
curl -b cookies.txt -X POST http://localhost:8080/admin/api/screens/hello/publish
curl http://localhost:8080/screens/hello | xxd | head   # must be .rc binary
```

## Docs to update (after implementation)

- `docs/history.md` — entry for T-007
- `README.md` — tick Etap 8 checkboxes
- `AGENTS.md` — add admin routes to module descriptions

## Out of scope

- OAuth / SSO authentication
- Multi-tenant / per-organization screens
- Component drag-and-drop visual editor (plain JSON textarea is sufficient for v1; JSON supports all `Component` types including `expandable` from T-008)
- General multi-state / tab editor UI (T-017)
- Experiment / A/B flag management (Etap 12)
