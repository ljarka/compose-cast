# server-admin — AGENTS.md

The admin panel's web UI (T-007, [ADR 0012](../docs/decisions/0012-admin-ui-technology.md),
[ADR 0016](../docs/decisions/0016-admin-storage-sqlite.md)): Ktor routes under `/admin`,
Freemarker templates, accounts and sessions on top of `server-store`. Mounted by `server-app`
only in admin mode (`COMPOSECAST_MODE=admin`); files mode has no `/admin`.

## Files

| File | Purpose |
|---|---|
| `AdminPanel.kt` | `Application.adminPanel(auth, screens, publishing, allowHttp)`: installs FreeMarker, mounts `adminRoutes`; `/admin/login` (GET/POST), `/admin/logout` (POST), `/admin` → `/admin/screens`, `loggedIn {}` (route-scoped `RequireUser`), `page(title)` (base template model), `seeOther`, `SecurityHeaders` |
| `ScreenPages.kt` | A3: `GET /admin/screens` (id, title, status `live`/`draft`/`unpublished`, last published), `POST /admin/screens` (create: id + title → unpublished draft), `GET /admin/screens/{id}` (editor: draft, else live), `POST /admin/screens/{id}` (save draft: valid only, stored canonical; 422 + problems otherwise), `POST /admin/screens/{id}/validate` (HTMX fragment); A4: `POST …/{id}/publish` (publishes the submitted textarea), `POST …/{id}/rollback` (form `revision`), `GET …/{id}/revisions/{revision}` (read-only old version); history table in the editor |
| `ScreenPublishing.kt` | `ScreenPublishing` (publish, rollback; implemented by `server-app`, which owns `LiveScreens`), `PublishException` (nothing changed) |
| `AdminAuth.kt` | Accounts and sessions: `bootstrapAdmin`, `createUser`, `login` → token, `user(token)`, `logout`; bcrypt (`at.favre.lib:bcrypt`) |
| `Role.kt` | `Role` (`viewer` < `editor` < `admin`, `canEdit`), `AdminUser` |
| `resources/templates/admin/*.ftl` | `_layout.ftl` (`<@layout.page title user role htmx>`: page, header with nav + logout, inline CSS light/dark, HTMX 2.0.11 with SRI), `login.ftl`, `screens.ftl`, `screen.ftl` (editor + history), `revision.ftl`, `_problems.ftl` (validation result; also the HTMX fragment) |

## Rules for new routes (A3–A5)

- Put every page/action inside `loggedIn { }` in `adminRoutes`; read the user with `call.adminUser`.
  The role policy is central: **any** non-GET/HEAD/OPTIONS method there is 403 for a `viewer`, 401
  without a session (GET redirects to the login page). `AdminPanelTest` walks the routing tree and
  checks every mutating route, so a new route is covered without a new test.
- Admin-only actions (users, settings) check `call.adminUser.role == Role.ADMIN` themselves.
- Templates: `${…}` is HTML-escaped everywhere (`outputFormat = HTMLOutputFormat`); never use `?no_esc`
  on user data; use `?url` in URLs. HTMX 2.0.11 from unpkg with an `integrity` hash (ADR 0012; same
  hash from jsdelivr, 2026-11-23) — load it only on pages that need it (`htmx=true`); no npm.
- POST handlers answer with 303 (`seeOther`) so reloads don't resubmit.

## Decisions

- **Sessions:** the cookie `composecast_admin` holds a random 256-bit token; the `sessions` table
  stores its SHA-256 (a copied database logs nobody in). TTL 7 days, not sliding; logout deletes the
  row; expired rows are deleted on lookup.
- **Cookie:** `Path=/admin; HttpOnly; SameSite=Strict; Secure`. `SameSite=Strict` is the CSRF
  protection (no cross-site request carries the cookie); no CSRF tokens. `Secure` unless
  `COMPOSECAST_ADMIN_ALLOW_HTTP=true`: over plain HTTP from a non-localhost host the login page
  says HTTPS is needed instead of failing silently (`X-Forwarded-Proto: https` from Caddy counts).
- **Passwords:** bcrypt cost 12 (tests: 4), 12 characters minimum, at most 71 UTF-8 bytes (the
  library throws above; `login` treats longer as wrong). Unknown emails are checked against a dummy
  hash (same timing). Emails are trimmed + lowercased. Login errors never say which part was wrong.
- **First admin:** `COMPOSECAST_ADMIN_EMAIL` / `_PASSWORD` when `users` is empty (`server-app`
  fails to start without them then); ignored once any account exists. More users: `createUser`
  (UI/CLI later; deleting users is out of scope for the MVP).
- Security headers on every `/admin` response: `Cache-Control: no-store`, `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`.
- **Screens (A3):** validation = `ScreenJson.check` (the same `problems()` as file loading, each with
  its JSON path) + the id must stay the URL's id. Invalid edits are never saved (422, the text stays
  in the textarea); valid ones are stored re-encoded (`ScreenJson.encode`), so the editor shows
  formatted JSON after saving. New ids: `[a-z0-9][a-z0-9_-]{0,63}`. Saving a draft never touches
  `live_json`, so apps are unaffected until A4's publish. Viewers get a read-only editor without HTMX.
- **Publish (A4):** the "Publish" button submits the textarea (`formaction`), so exactly what is
  shown goes live (and the draft is cleared); it is checked like a draft first. Building the whole
  registry is `server-app`'s job (`AdminScreens.publish`): build with the change → write → swap,
  under one lock; any failure (unset `{{secret.X}}`, …) is a `PublishException` and changes
  nothing. Rollback keeps a pending draft. Editors may publish (roles: ADR 0016).
- Not yet: login rate limiting (bcrypt cost is the only brake), password change, user management UI.

Ktor 3.6.1 facts (sources jar, 2026-11-23): `Sessions` is route-scoped (installed on `/admin` only),
`FreeMarker` is application-scoped (FreeMarker 2.3.35; `?url` throws unless `urlEscapingCharset`
is set); a response sent in a route-scoped plugin's
`onCall` skips the route handler (`RoutingNode` checks `isHandled`); `Secure` cookies are sent over
HTTP without error; `RoutingNode.getAllRoutes()` lists endpoints.

## Dependencies

`:protocol` (`ScreenJson`), `:server-store` (users, sessions, screens); Ktor core, sessions, freemarker; bcrypt. Versions in
`gradle/libs.versions.toml`. Never depends on `server-app` (R10).

## Tests

`AdminTestHost` (temp database + routes on the Ktor test host, `loginAs(role)`). `AdminPanelTest`
(redirect without session, env admin login + cookie attributes, logout, wrong password/unknown
email, viewer 403 / editor allowed on every mutating route found in the routing tree, HTTPS
message, `allowHttp`, HTML escaping, bootstrap rules). `ScreenPagesTest` (list + statuses, create,
invalid edit → problems with paths and nothing saved, canonical draft leaves `live()` unchanged,
validate fragment, editor vs. viewer page, no textarea breakout, publish + who + draft cleared,
invalid/failed publish changes nothing, rollback + revision page). The host's fake `publishing`
writes straight to the store (`failWith` simulates a build failure).

```bash
./gradlew :server-admin:test
```
