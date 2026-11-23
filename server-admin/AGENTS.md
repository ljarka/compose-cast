# server-admin — AGENTS.md

The admin panel's web UI (T-007, [ADR 0012](../docs/decisions/0012-admin-ui-technology.md),
[ADR 0016](../docs/decisions/0016-admin-storage-sqlite.md)): Ktor routes under `/admin`,
Freemarker templates, accounts and sessions on top of `server-store`. Mounted by `server-app`
only in admin mode (`COMPOSECAST_MODE=admin`); files mode has no `/admin`.

## Files

| File | Purpose |
|---|---|
| `AdminPanel.kt` | `Application.adminPanel(auth, allowHttp)`: installs FreeMarker, mounts `adminRoutes`; `/admin/login` (GET/POST), `/admin/logout` (POST), `loggedIn {}` (route-scoped `RequireUser`), `SecurityHeaders` |
| `AdminAuth.kt` | Accounts and sessions: `bootstrapAdmin`, `createUser`, `login` → token, `user(token)`, `logout`; bcrypt (`at.favre.lib:bcrypt`) |
| `Role.kt` | `Role` (`viewer` < `editor` < `admin`, `canEdit`), `AdminUser` |
| `resources/templates/admin/*.ftl` | `_layout.ftl` (`<@layout.page title>`: page + inline CSS, light/dark), `login.ftl`, `home.ftl` |

## Rules for new routes (A3–A5)

- Put every page/action inside `loggedIn { }` in `adminRoutes`; read the user with `call.adminUser`.
  The role policy is central: **any** non-GET/HEAD/OPTIONS method there is 403 for a `viewer`, 401
  without a session (GET redirects to the login page). `AdminPanelTest` walks the routing tree and
  checks every mutating route, so a new route is covered without a new test.
- Admin-only actions (users, settings) check `call.adminUser.role == Role.ADMIN` themselves.
- Templates: `${…}` is HTML-escaped everywhere (`outputFormat = HTMLOutputFormat`); never use `?no_esc`
  on user data. HTMX (A3+) from unpkg with an integrity hash (ADR 0012); no npm.
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
- Not yet: login rate limiting (bcrypt cost is the only brake), password change, user management UI.

Ktor 3.6.1 facts (sources jar, 2026-11-23): `Sessions` is route-scoped (installed on `/admin` only),
`FreeMarker` is application-scoped (FreeMarker 2.3.35); a response sent in a route-scoped plugin's
`onCall` skips the route handler (`RoutingNode` checks `isHandled`); `Secure` cookies are sent over
HTTP without error; `RoutingNode.getAllRoutes()` lists endpoints.

## Dependencies

`:server-store` (users, sessions); Ktor core, sessions, freemarker; bcrypt. Versions in
`gradle/libs.versions.toml`. Never depends on `server-app` (R10).

## Tests

`AdminPanelTest` (Ktor test host: redirect without session, env admin login + cookie attributes,
logout, wrong password/unknown email, viewer 403 / editor 200 on every mutating route, HTTPS
message, `allowHttp`, HTML escaping, bootstrap rules).

```bash
./gradlew :server-admin:test
```
