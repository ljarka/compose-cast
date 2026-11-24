# server-admin — AGENTS.md

The admin panel's web UI (T-007, [ADR 0012](../docs/decisions/0012-admin-ui-technology.md),
[ADR 0016](../docs/decisions/0016-admin-storage-sqlite.md)): Ktor routes under `/admin`,
Freemarker templates, accounts and sessions on top of `server-store`. Mounted by `server-app`
only in admin mode (`COMPOSECAST_MODE=admin`); files mode has no `/admin`.

## Files

| File | Purpose |
|---|---|
| `AdminPanel.kt` | `Application.adminPanel(auth, screens, settings, publishing, allowHttp)`: installs FreeMarker, mounts `adminRoutes`; `/admin/login` (GET/POST), `/admin/logout` (POST), `/admin` → `/admin/screens`, `loggedIn {}` (route-scoped `RequireUser`), `page(title)` (base template model), `seeOther`, `SecurityHeaders` |
| `ScreenPages.kt` | A3: `GET /admin/screens` (id, title, status `live`/`draft`/`unpublished`, `home` badge + `Set as home` on live screens, last published), `POST /admin/screens` (create: id + title → unpublished draft), `POST …/{id}/home` (sets `HOME_SCREEN` for live screen), `GET /admin/screens/{id}` (editor: draft, else live), `POST /admin/screens/{id}` (save draft: valid only, stored canonical; 422 + problems otherwise), `POST /admin/screens/{id}/validate` (HTMX fragment); A4: `POST …/{id}/publish` (publishes the submitted textarea), `POST …/{id}/rollback` (form `revision`), `GET …/{id}/revisions/{revision}` (read-only old version); history table in the editor; T-045 B2: `POST …/{id}/preview` (the textarea → `publishing.preview` → QR of `<server>/connect?screen=__preview-…`, ADR 0027; `publicServerUrl()` = `server-app`'s `ConnectPage.serverUrl` rule, keep both equal) |
| `SettingsPages.kt` | A5: `GET/POST /admin/settings` (multipart form: app name = brand name, applicationId, server URL, brand colors, logo upload/remove; POST admin-only), `GET /admin/settings/logo`; `AppKeys` (`app.name`, `app.applicationId`, `app.serverUrl`, `app.icon` base64 + `app.icon.type`: read by T-010 export) |
| `FormSchema.kt` | T-029 C2 (ADR 0028): property forms from `SerialDescriptor`s — `Kind` (Text, Number, Toggle, Choice, Group, Variant, Dimension, JsonValue), `componentFields(type)`, `screenFields`, `componentLists`, `values` (JSON → form values, defaults written out), `parse` (form → JSON, path-named problems), `filledIn` |
| `ScreenTree.kt` | Outline rows of a screen's JSON, node paths (`components.0.children.2`), `get`/`set` |
| `VisualPages.kt` | `GET/POST /admin/screens/{id}/visual?node=` (tree + form; save = whole screen checked, then `saveDraft`), `POST …/visual/panel` (HTMX re-render, e.g. another action type; saves nothing), `POST …/visual/edit` (C3: `op` = add-after/add-inside/add-end/up/down/into-previous/out/duplicate/delete/wrap, `type`), `?node=` may be a list (`components.2.empty`), `formRows` (flat rows for `_form.ftl`), `HELP` texts |
| `Palette.kt` | T-029 C3: `starters` (smallest valid component per type; a repeat gets the screen's first data source), `groups` (incl. "Dashboard": gauge, sparkline, kpi — T-047), `containers`, structural edits on node paths: `add`, `delete`, `duplicate`, `move`, `intoPrevious`, `outOfParent`, `wrap` → `Result(screen, select)` |
| `Bindings.kt` | `rename` of a binding root in JSON strings (renaming a repeat's `as`) |
| `ApiProbe.kt` | T-031: server-side GET of an API for the wizard: public hosts only (each redirect hop resolved + `HttpImageLoader.isPrivateAddress`), ≤ 2 MB (the screens' limit; TVMaze `/shows` is 373 KB), object/array JSON only, `{{secret.NAME}}` from env; `Result(status, contentType, millis, json, error)` |
| `ApiShape.kt` | T-031: `lists` (arrays of objects, largest first), item `fields` (path, sample, `Kind`), `roles` (id/title/subtitle/image guesses) |
| `WizardScreens.kt` | T-031 W2: `Plan` → `list(plan)` (column › repeat of cards: 72 dp image, title, 2-line subtitle, tap → detail; max 20 items) and `detail(plan)` (own source `…/{{params.id}}` with `params.id` default = a real id, or the list's fields passed as params); HTML fields through `plain` |
| `TemplatesPages.kt` | T-030 ([ADR 0031](../docs/decisions/0031-app-bundle-format.md)): `GET /admin/templates` (bundled templates from `app-templates`), `POST /admin/templates/{t}` (prefix, "use its colors" → drafts via `withPrefix`; clashing ids → 422 with a prefix hint; colors only, name/font kept, via `publishBrand`), `POST /admin/templates/{t}/publish` (publishes the drafts, sets the template's home); `/admin` of an app without screens redirects here; `templates.ftl` |
| `WizardPages.kt` | `GET/POST /admin/wizard` (`step` = probe/create): probe → pick list + fields (guesses pre-filled, another list = its own guesses) → both screens as drafts → visual editor; refuses raw keys in headers/URL (`{{secret.NAME}}` only) and existing ids |
| `DataFields.kt` | T-031 W3: the field picker's bindings at a node (`groups`: repeat alias items first, then each source, then params) from probed data |
| `QrCode.kt` | `QrCode.svg(text)`: inline SVG QR code (`io.nayuki:qrcodegen`, ECC medium); the only template value rendered with `?no_esc` (generated, no user text) |
| `ScreenPublishing.kt` | `ScreenPublishing` (publish, rollback, publishBrand, preview(json, owner) + updatePreview + stopPreviews — T-032, ADR 0030: one live preview per user and screen, every draft save updates it, logout stops it; implemented by `server-app`, which owns `LiveScreens`), `PublishException` (nothing changed) |
| `AdminAuth.kt` | Accounts and sessions: `bootstrapAdmin`, `createUser`, `login` → token, `user(token)`, `logout`; bcrypt (`at.favre.lib:bcrypt`) |
| `Role.kt` | `Role` (`viewer` < `editor` < `admin`, `canEdit`), `AdminUser` |
| `resources/templates/admin/*.ftl` | `_layout.ftl` (`<@layout.page title user role htmx>`: page, header with nav + logout, inline CSS light/dark, HTMX 2.0.11 with SRI), `login.ftl`, `screens.ftl`, `screen.ftl` (editor + history), `revision.ftl`, `settings.ftl`, `wizard.ftl` (data source wizard), `_fields.ftl` (field picker fragment), `visual.ftl` + `_form.ftl` (visual editor; groups are `<details>`, closed unless filled), `_problems.ftl` (validation result; also the HTMX fragment) |

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
- **Settings (A5):** one "App name" field is both `BrandConfig.name` (`/brand`) and `app.name`.
  Validation names the field (`BrandConfig.problems()` from `protocol`, applicationId = the Gradle
  plugin's rule, server URL http/https with a host, logo: square PNG/JPEG ≤ 1 MB, checked by
  magic bytes + `ImageIO`). The brand goes live through `publishBrand` (server-app rebuilds every
  screen first; failure = nothing saved); app keys are written only after that. No draft for
  settings: saving is publishing. Without an imported brand the form shows the renderer's defaults.
- **Preview (T-045 B2, ADR 0027):** saves nothing; the QR and an "open on this phone" link go
  to `/connect?screen=…`; the server URL comes from `X-Forwarded-Proto` + `Host` (fallback: the
  request's host/port, e.g. the test engine sends no `Host`).
- **Visual editor, palette, data source wizard, field picker:** how they work and why —
  [docs/admin-editor.md](../docs/admin-editor.md) (read it before changing `FormSchema`, `Palette`,
  `VisualPages`, `Wizard*`, `ApiProbe`, `ApiShape`, `DataFields`).
- Not yet: login rate limiting (bcrypt cost is the only brake), password change, user management UI.

Ktor 3.6.1 facts (sources jar, 2026-11-23): `Sessions` is route-scoped (installed on `/admin` only),
`FreeMarker` is application-scoped (FreeMarker 2.3.35; `?url` throws unless `urlEscapingCharset`
is set); a response sent in a route-scoped plugin's
`onCall` skips the route handler (`RoutingNode` checks `isHandled`); `Secure` cookies are sent over
HTTP without error; `RoutingNode.getAllRoutes()` lists endpoints.

## Dependencies

`:protocol` (`ScreenJson`), `:server-store` (users, sessions, screens), `:sdk-server` (only `HttpImageLoader.isPrivateAddress` for the probe); Ktor core, sessions, freemarker; bcrypt. Versions in
`gradle/libs.versions.toml`. Never depends on `server-app` (R10).

## Tests

`AdminTestHost` (temp database + routes on the Ktor test host, `loginAs(role)`, fake `publishing`
and `apiResponses` for the probe). One test class per page/engine; the catalogue and the
acceptance tests (`BuildFromEmptyTest`, TVMaze wizard) are in [docs/admin-editor.md](../docs/admin-editor.md#tests).

```bash
./gradlew :server-admin:test
```

Looking at a page without a browser tool: headless Chrome screenshots — [docs/admin-editor.md](../docs/admin-editor.md#looking-at-a-page).
