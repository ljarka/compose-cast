# Roadmap

> Part of the project constitution: [mission](mission.md) (why) · [tech stack](tech-stack.md)
> (how) · **roadmap** (what and when). One step = one task file in [docs/tasks/](../docs/tasks/README.md)
> (one agent session, one reviewable series of commits). Steps are done in order; the task queue
> holds the detail and the status.

Goal of this roadmap: the two success criteria in the [mission](mission.md#what-success-looks-like-12-months) —
**a non-developer ships an app** and **first screen in under 30 minutes**.

## Where we are (2026-11-23)

Done: screen model (JSON + DSL), per-request rendering with version negotiation, data sources and
bindings, component catalog (layouts, text, images, cards, buttons, grids, adaptive, a11y), brand
colors, Docker distribution (GHCR, Caddy), Android app without Kotlin (plugin on Reposilite,
release signing), data-dir hot reload, releases `v0.1.0` and `v0.2.0`. Milestone 1 proof: the
[weather app](https://github.com/ljarka/weather-app) built from JSON only.

## Steps

### 0. Close Milestone 1

| # | Step | Done when… |
|---|---|---|
| 0.1 | Deploy the weather app on a VPS over HTTPS (T-025; waits for the owner's domain) | The app built with the release config runs against `https://<domain>`; Etap 6 ticked in the [feature checklist](../docs/feature-checklist.md) |

### A. Admin panel MVP (T-007, split) — the smallest vertical slice

| # | Step | Done when… |
|---|---|---|
| A1 | `server-store`: SQLite (Exposed), `screens` table with canonical screen JSON, import of `screens/*.json` at first start; *admin mode* serves from the database via `LiveScreens` | A server in admin mode serves the imported screens byte-identically; files mode unchanged |
| A2 | Admin login: users (bcrypt), sessions, roles, first admin from `COMPOSECAST_ADMIN_EMAIL`/`_PASSWORD` | `/admin` redirects to login; the env admin logs in; a viewer cannot edit |
| A3 | Screen list + JSON editor with live `problems()` validation, save as draft | Editing a screen shows errors with JSON paths while typing; a draft does not change what apps see |
| A4 | Publish + history + rollback | Publish makes the draft live within one request (no restart); every publish is a history entry; rollback restores a previous version |
| A5 | App Settings page: app name, applicationId, server URL, brand colors, logo | Brand edits change `/brand` and the rendered screens after save |

### B. See it on a phone

| # | Step | Done when… |
|---|---|---|
| B1 | Generic **compose-cast Preview** app (built from `app-template`, published in GitHub Releases; [ADR 0024](../docs/decisions/0024-generic-preview-app.md)) | Scanning the admin's QR code connects the installed preview app to the owner's server |
| B2 | Draft preview: `__preview` screen per session, QR in the editor (ADR 0013 mechanics) | A saved draft is visible in the preview app before publishing |
| B3 | **Measure "first screen in under 30 minutes"** with the install docs | Fresh VPS → own screen on a phone, time written down; docs fixed until it is < 30 min |

### C. Visual editor (T-029) — no JSON for the owner

| # | Step | Done when… |
|---|---|---|
| C1 | ADR: admin UI technology for the visual editor (HTMX + JS islands vs. a JS framework) | ADR accepted; [tech stack](tech-stack.md) updated if it changes |
| C2 | Component tree + schema-driven property forms for existing screens | Every property of every component is editable in a form; JSON stays in sync |
| C3 | Component palette: add, remove, reorder, nest | A screen can be built from empty without touching JSON |

### D. Start from something that works

| # | Step | Done when… |
|---|---|---|
| D1 | Data source wizard: paste an API URL → field picker → list + detail screens (T-031; before the templates, which need data sources — owner 2026-11-23) | A public API (e.g. TVMaze) becomes a list and a detail screen without JSON |
| D2 | App templates in the admin: news, catalog, events, weather (T-030) | Choosing a template creates a working multi-screen app in one click |

### G. Live dashboards (owner, 2026-11-24; done after D, before E)

Static screens stay exactly as they are; **live** is opt-in per screen for advanced screens
([ADR 0029](../docs/decisions/0029-live-screens-websocket-data-updates.md): WebSocket + RC
data-update documents, PoC: 10 updates/s on the emulator).

| # | Step | Done when… |
|---|---|---|
| G1 | Dashboard components on RC `Canvas`: `gauge`, `sparkline`, `kpi` (T-047) | Golden `dashboard.png` at two widths; editable in the visual editor |
| G2 | Live screens: `live.everyMs`, WebSocket `/screens/{id}/live`, data updates in `sdk-android` (T-047) | The demo dashboard moves every second on the emulator with no refresh button; static screens byte-identical |
| G3 | Live edit preview on the same channel (T-032) | An edit in the panel shows on the phone in under 2 s |

### E. Ship the owner's app

| # | Step | Done when… |
|---|---|---|
| E1 | Export the app repository from App Settings: `compose-cast.properties`, launcher icon, `.gitignore` (T-010) | The exported repository builds unchanged with `./gradlew assembleRelease` |
| E2 | GitHub Actions workflow in the export: signed AAB from keystore + passwords in GitHub secrets ([ADR 0025](../docs/decisions/0025-owner-app-built-by-github-actions.md); T-044 signing) | Pushing the exported repository to GitHub produces a signed AAB artifact |
| E3 | Guided docs: create keystore, GitHub secrets, upload to Google Play | A non-developer follows them without help |

### F. Proof: a non-developer ships an app

| # | Step | Done when… |
|---|---|---|
| F1 | Images loaded by the SDK for long lists (T-027) | A 50-item list with images scrolls smoothly; documents stay small |
| F2 | Text input for search via RC `Custom` (T-021) | A search field drives a data-source parameter |
| F3 | **TV shows app** (TVMaze) built only in the admin panel, exported, built by GitHub Actions | Signed AAB from the workflow; screenshots and time in the README — success criterion 1 met |

## After the success criteria

Not ordered yet; each becomes a step when a real app needs it: feature flags and A/B tests (T-038), analytics in the panel (T-037), AI assistant
(T-033), offline cache (T-028), canvas charts (T-018), forms (T-034), app user accounts (T-035),
push (T-036), stateful components (T-017), gestures/effects (T-019), dynamic values incl.
light/dark (T-020), advanced RC features (T-022).

## Changing this roadmap

Reorder or add steps here first, then mirror the order in the [task queue](../docs/tasks/README.md).
A step that changes an accepted decision starts with an ADR.
