# Task queue

**If you are an agent without a specific request from the owner: take the task below whose status
is exactly `ready` (there is at most one), read its file and do exactly that.** Tasks implement the
[roadmap](../../specs/roadmap.md) in its order.

| Task | Roadmap | Status |
|---|---|---|
| [T-001 Typed actions](T-001-typed-actions.md) | Etap 2 | done (`8c8dabb`) |
| [T-002 Serializable screen model + JSON screens](T-002-screen-model-json.md) | Etap 2 | done (`8e3e935`) |
| [T-003 Per-request rendering, route params, ETag](T-003-per-request-rendering.md) | Etap 2 | done (`d2c4de6`) |
| [T-009 Backend in one command (Docker)](T-009-docker.md) | Etap 6 | done (`7405b52`) |
| [T-006 Data sources](T-006-data-sources.md) | Etap 4 | done (`c6b0b3a`) |
| [T-008 Local state spike](T-008-local-state-spike.md) | Etap 5/10 | done (`0cc6cae`) |
| [T-011 `app-template` configurable without code](T-011-app-template-config.md) | Etap 7 | done (`ba52457`, completed in `dc64663`) |
| [T-023 Server distribution like Immich (GHCR + release assets)](T-023-app-repositories.md) | Etap 6 | done (`2df0dc0`; owner: first GitHub Release, HTTPS check on the VPS) — ADR 0018 |
| [T-024 Android artifacts on Reposilite + `io.composecast.app` plugin](T-024-android-artifacts.md) | Etap 6 | done (`51aa515`; owner: Reposilite on the VPS + GitHub secrets, then a release publishes) |
| [T-012 Layout containers and common modifiers](T-012-layout-containers.md) | Etap 5 | done (`a444bf4`) — the weather app needs rows/columns |
| [T-013 Text/image options, Icon, Divider, Card, button variants](T-013-text-image-styling.md) | Etap 5 | done (`b75647f`) |
| [T-004 Version negotiation](T-004-version-negotiation.md) | Etap 7 | done (`da06bdd`) |
| [T-014 Scrolling, Flow, galleries, grids](T-014-scroll-flow-gallery.md) | Etap 5 | done (`70e3329`) |
| [T-015 Adaptive layouts (FitBox, collapsible)](T-015-adaptive-layouts.md) | Etap 5 | done (`532da5c`) |
| [T-016 Accessibility semantics](T-016-accessibility.md) | Etap 5 | done (`bd3cd2c`) |
| [T-026 Image fetch hardening (no private addresses)](T-026-image-fetch-hardening.md) | Etap 6 | done (`3c81c74`) |
| [T-039 Split large files and long AGENTS.md](T-039-split-large-files.md) | chores | done (`5c145b0`) — renderer → `render/` package, protocol model in 4 files, demo screens one per file |
| [T-025 Milestone 1: weather app in its own repo + measurements](T-025-weather-app-milestone.md) | Etap 6 | in progress (waiting for owner: domain) — `v0.1.0` released, app runs on it: [ljarka/weather-app](https://github.com/ljarka/weather-app) |
| [T-043 App bar shows the screen id while loading](T-043-shell-loading-title.md) | Etap 7 | done (`7241097`) |
| [T-040 Brand theme colors for component defaults](T-040-brand-theme-colors.md) | Etap 5 | done (`a88be28`) |
| [T-041 Lookups keyed by more than one value (day/night)](T-041-compound-lookup-keys.md) | Etap 4 | done (`9689de6`) |
| [T-042 Reload the data directory without a restart](T-042-reload-data-dir.md) | Etap 6 | done (`383c443`) |
| [T-044 Release signing configured by the app repository](T-044-release-signing.md) | Etap 6 | done (`72bb93c`) |
| [T-007 Admin panel MVP](T-007-admin-panel.md) | Etap 8 | done (`6ba75e7`) — A1–A5: `server-store`, admin mode, login + roles, editor + drafts, publish + history + rollback, settings, admin mode in Docker |
| [T-045 Generic Preview app + draft preview](T-045-preview-app.md) | Roadmap B | waiting for the owner — B1 Preview app + `/connect`, B2 draft preview done; B3 needs a fresh VPS + signing secrets (T-029 goes on meanwhile) |
| [T-029 Visual screen editor (no JSON)](T-029-visual-screen-editor.md) | Etap 8 | done (`69010e0`) — C1 ADR 0028, C2 tree + generated forms, C3 palette; acceptance test rebuilds the weather `main` |
| [T-031 Data source wizard (URL → screens)](T-031-data-source-wizard.md) | Etap 8 | done (`001e06c`) — W1 probe + heuristics, W2 wizard (TVMaze list + detail on the emulator), W3 field picker + data source form |
| [T-047 Live screens + dashboard components](T-047-live-dashboards.md) | Roadmap G1–G2 | done (`99a932a`) — gauge/sparkline/kpi, live screens over a WebSocket, app keeps them current |
| [T-032 Live preview on a phone](T-032-live-preview.md) | Roadmap G3 | done (`1087b4b`) — one live preview per editor and screen, every saved edit on the phone in ~1 s (ADR 0030) |
| [T-030 App templates](T-030-app-templates.md) | Etap 8 | done (`4ab4a14`) — News, Shop, Events, Weather (live), Blank; Templates page applies drafts + Publish all (ADR 0031) |
| [T-048 Phone preview inside the admin panel](T-048-in-panel-phone-preview.md) | Roadmap G4 | done (`c06d5ea`) — optional `preview-renderer` service (real player under Robolectric, linux/amd64), phone frame in the visual editor, edit → image 0.14 s (ADR 0032) |
| [T-049 Custom components written in RC JSON](T-049-custom-components-rc-json.md) | Roadmap G5 | done (`12697bc`) — custom components in raw RC JSON, admin Components page, palette, ADR 0033 |
| [T-010 Settings export (properties + icon)](T-010-app-export.md) | Etap 9 | in progress — Design rewritten; next: AppExportService + route |
| [T-027 Images loaded by the SDK (lists)](T-027-sdk-loaded-images.md) | Etap 5 | queued (after T-010); roadmap F1 |
| [T-021 Native components via RC `Custom` (text input)](T-021-native-components.md) | Etap 10 | queued (after T-027); roadmap F2; spike first |
| [T-046 Milestone 2: TV shows app built only in the admin](T-046-tv-shows-milestone.md) | Roadmap F | queued (after T-021); roadmap F3 — success criterion 1 |
| [T-038 Feature flags and A/B tests](T-038-feature-flags-ab.md) | Etap 12 | queued (after T-046) |
| [T-037 Analytics in the panel](T-037-analytics.md) | Etap 12 | queued (after T-038) |
| [T-033 AI assistant in the panel](T-033-ai-assistant.md) | Etap 8 | queued (after T-037) |
| [T-028 Offline: disk cache of the last screen](T-028-offline-disk-cache.md) | Etap 7 | queued (after T-033) |
| [T-018 Canvas: progress, charts, gradients](T-018-canvas-charts.md) | Etap 5 | queued (after T-028) |
| [T-034 Forms and submit](T-034-forms-submit.md) | Etap 10 | queued (after T-018) |
| [T-035 App user accounts (Supabase)](T-035-app-user-auth-supabase.md) | Etap 10 | queued (after T-034) |
| [T-036 Push notifications (FCM)](T-036-push-notifications.md) | Etap 12 | queued (after T-035) |
| [T-017 Stateful components (tabs, toggles, visibility)](T-017-stateful-components.md) | Etap 10 | queued (after T-036) |
| [T-019 Gestures and visual effects](T-019-gestures-effects.md) | Etap 5 | later (only when a real app needs it) |
| [T-020 Dynamic values (clock, light/dark, conditions)](T-020-dynamic-values.md) | Etap 7/10 | later (light/dark may be pulled forward by a real app) |
| [T-022 Advanced RC features (templates, drag, sensors)](T-022-advanced-rc-features.md) | Etap 10 | later (pick by real use case) |
| [T-050 Close CI verification gaps (`app-templates` and `sdk-android` tests)](T-050-ci-gaps-and-sdk-android-tests.md) | quality | done (`9ec5ede`) |
| [T-051 Server security hardening (SSRF, login rate limit, 500, CSP)](T-051-server-security-hardening.md) | quality | done (`3fa809d`) |
| [T-052 Android client cleanup (single OkHttp engine, timeouts, release logging)](T-052-sdk-android-cleanup.md) | quality | done |
| [T-053 SQLite schema migrations, foreign keys, and expired session cleanup](T-053-sqlite-migrations-and-sessions.md) | quality | in progress |
| [T-054 Server I/O offloading, database-aware `/health`, and graceful shutdown](T-054-server-io-health-shutdown.md) | quality | queued (after T-053) |
| [T-055 Static analysis (`detekt`) and `explicitApi()` for published libraries](T-055-explicit-api-and-detekt.md) | quality | queued (after T-054) |
| [T-056 Live screen fan-out and refactoring of largest files](T-056-live-fanout-and-refactoring.md) | quality | queued (after T-055) |
| [T-005 iOS spike](T-005-ios-spike.md) | Etap 3 | dropped (Android only, ADR 0022) |

Rows are in priority order, mirroring [specs/roadmap.md](../../specs/roadmap.md) (constitution,
owner 2026-11-23): admin panel MVP (T-007, steps A1–A5) → Preview app (T-045) → visual editor
(T-029) → data wizard (T-031) → live screens + dashboards (T-047) → live edit preview (T-032) → templates (T-030) (owner 2026-11-24: live first, templates build on it) → phone preview in the panel (T-048, owner 2026-11-24) → custom components in RC JSON (T-049, owner 2026-11-26) → export + GitHub Actions build (T-010) → SDK
images (T-027) → text input (T-021) → TV shows app built only in the admin (T-046, success
criterion 1). Rows after T-046 are not ordered yet. T-025 only waits for the owner's domain.
Reorder the roadmap first, then this table. Statuses `later` and `dropped` are never picked
automatically.
Owner, 2026-11-08 / 2026-11-11 ([ADR 0017](../decisions/0017-apps-in-own-repositories.md)): the
weather app is an **external example of using compose-cast** (in its own separate repository, not
part of this project) and needs no framework code changes — server from the published
image on a VPS, app = `app-template` configured; missing features are added here, generically.
**Everything app-specific must be editable in the admin panel** (T-007) — never backend code.
Distribution like Immich ([ADR 0018](../decisions/0018-distribution-like-immich.md)): compose file +
`example.env` from GitHub Releases, image on GHCR, Android SDK + Gradle plugin on the owner's Reposilite
([ADR 0019](../decisions/0019-self-hosted-maven-reposilite.md)); apps stay branded, one repository each.
T-012…T-022 (owner, 2026-11-07: support every component Remote Compose can express) cover the
catalog in [docs/remote-compose.md → Procedural catalog](../remote-compose.md).

Statuses: `ready` (start now; only one task at a time) · `queued (after T-xxx)` · `in progress` ·
`blocked: reason` · `needs design detail` · `later` · `dropped` · `done` (+ commit hash once committed).
When a task is done, the next `queued` task becomes `ready`.

## Context budget (ADR 0023)

Read only: root `AGENTS.md` → the task file → the `AGENTS.md` of the modules in its `Module:` line
(plus the ADRs they link). Do not load other modules. If a task needs more, split it.

## Working a task

1. Set its status to `in progress` here.
2. Follow the task file; when reality differs from the plan, update the task file first, then code.
3. Finish with the [Definition of Done](../../AGENTS.md#definition-of-done-every-task) and
   `./scripts/verify.sh`.
4. Mark it `done`, set the next task to `ready`, tick the [feature checklist](../feature-checklist.md) boxes, and make sure the
   next task is specified well enough to start (files, acceptance criteria) — a fresh agent must be
   able to continue without asking. Commit the finished work (AGENTS.md rule 7); push only when the owner asks.

## Task file template

```markdown
# T-NNN — Title
Status: … · Roadmap: Etap N · Depends on: …

## Goal
## Why (links to ADRs)
## Design
## Steps (files to touch)
## Acceptance criteria
## Verification
## Docs to update
## Out of scope
```
