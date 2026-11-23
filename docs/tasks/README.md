# Task queue

**If you are an agent without a specific request from the owner: take the task below whose status
is exactly `ready` (there is at most one), read its file and do exactly that.** Tasks implement the
[README roadmap](../../README.md#roadmap) in dependency order.

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
| [T-025 Milestone 1: weather app in its own repo + measurements](T-025-weather-app-milestone.md) | Etap 6 | in progress (waiting for owner: domain, push target) — `v0.1.0` released, app runs on it in `../weather-app` |
| [T-043 App bar shows the screen id while loading](T-043-shell-loading-title.md) | Etap 7 | done (`7241097`) |
| [T-040 Brand theme colors for component defaults](T-040-brand-theme-colors.md) | Etap 5 | ready — gap from T-025 |
| [T-041 Lookups keyed by more than one value (day/night)](T-041-compound-lookup-keys.md) | Etap 4 | queued (after T-040) — gap from T-025 |
| [T-042 Reload the data directory without a restart](T-042-reload-data-dir.md) | Etap 6 | queued (after T-041) — gap from T-025 |
| [T-044 Release signing configured by the app repository](T-044-release-signing.md) | Etap 6 | queued (after T-042) — gap from T-025 |
| [T-007 Admin panel MVP](T-007-admin-panel.md) | Etap 8 | queued (after T-044 and T-025); **core of the product**; first step: write the MVP cut into the task file |
| [T-029 Visual screen editor (no JSON)](T-029-visual-screen-editor.md) | Etap 8 | queued (after T-007) |
| [T-031 Data source wizard (URL → screens)](T-031-data-source-wizard.md) | Etap 8 | queued (after T-029) |
| [T-030 App templates](T-030-app-templates.md) | Etap 8 | queued (after T-031) |
| [T-032 Live preview on a phone](T-032-live-preview.md) | Etap 8 | queued (after T-030) |
| [T-038 Feature flags and A/B tests](T-038-feature-flags-ab.md) | Etap 12 | queued (after T-032) |
| [T-037 Analytics in the panel](T-037-analytics.md) | Etap 12 | queued (after T-038) |
| [T-033 AI assistant in the panel](T-033-ai-assistant.md) | Etap 8 | queued (after T-037) |
| [T-027 Images loaded by the SDK (lists)](T-027-sdk-loaded-images.md) | Etap 5 | queued (after T-033); blocks the TV shows milestone |
| [T-028 Offline: disk cache of the last screen](T-028-offline-disk-cache.md) | Etap 7 | queued (after T-027) |
| [T-018 Canvas: progress, charts, gradients](T-018-canvas-charts.md) | Etap 5 | queued (after T-028; pull it forward if T-025 needs an hourly chart) |
| [T-021 Native components via RC `Custom` (text input)](T-021-native-components.md) | Etap 10 | queued (after T-018); spike first; needed for TV shows search |
| [T-034 Forms and submit](T-034-forms-submit.md) | Etap 10 | queued (after T-021) |
| [T-035 App user accounts (Supabase)](T-035-app-user-auth-supabase.md) | Etap 10 | queued (after T-034) |
| [T-036 Push notifications (FCM)](T-036-push-notifications.md) | Etap 12 | queued (after T-035) |
| [T-017 Stateful components (tabs, toggles, visibility)](T-017-stateful-components.md) | Etap 10 | queued (after T-036) |
| [T-010 Settings export (properties + icon)](T-010-app-export.md) | Etap 9 | needs design detail (after T-007; much smaller since the plugin, see the note in the task file) |
| [T-019 Gestures and visual effects](T-019-gestures-effects.md) | Etap 5 | later (only when a real app needs it) |
| [T-020 Dynamic values (clock, light/dark, conditions)](T-020-dynamic-values.md) | Etap 7/10 | later (light/dark may be pulled forward by a real app) |
| [T-022 Advanced RC features (templates, drag, sensors)](T-022-advanced-rc-features.md) | Etap 10 | later (pick by real use case) |
| [T-005 iOS spike](T-005-ios-spike.md) | Etap 3 | dropped (Android only, ADR 0022) |

Rows are in priority order. **T-025 gaps (2026-11-18):** T-025 waits for the owner (domain, push target; `v0.1.0` released 2026-11-23);
the small gaps it found (T-043, T-040, T-041, T-042, T-044) run meanwhile, before the admin panel. **Re-prioritized 2026-11-16 (owner):** real apps before catalog
breadth. **The admin panel is the core of the product** (owner: anyone can create the screens
of their mobile app in the admin panel). Order: Milestone 1 (T-025) proves the runtime → admin panel MVP
(T-007) → visual editor (T-029). The gap lists and its gap list decides what follows. Catalog tasks
without a real use case (T-019, T-020, T-022) are `later`. Statuses `later` and `dropped` are never
picked automatically.
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
4. Mark it `done`, set the next task to `ready`, tick the README roadmap boxes, and make sure the
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
