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
| [T-011 `app-template` configurable without code](T-011-app-template-config.md) | Etap 7 | done (commit TBD) |
| [T-023 Server distribution like Immich (GHCR + release assets)](T-023-app-repositories.md) | Etap 6 | ready |
| [T-024 Android artifacts on Reposilite + `io.composecast.app` plugin](T-024-android-artifacts.md) | Etap 6 | queued (after T-023; owner: Reposilite on the VPS, ADR 0019) |
| [T-012 Layout containers and common modifiers](T-012-layout-containers.md) | Etap 5 | queued (after T-011) — the weather app needs rows/columns |
| [T-013 Text/image options, Icon, Divider, Card, button variants](T-013-text-image-styling.md) | Etap 5 | queued (after T-012) |
| [T-004 Version negotiation](T-004-version-negotiation.md) | Etap 7 | queued (after Milestone 1, Etap 6) — no installed apps before that; the app already sends its player version |
| [T-014 Scrolling, Flow, galleries, grids](T-014-scroll-flow-gallery.md) | Etap 5 | queued (after T-004) |
| [T-015 Adaptive layouts (FitBox, collapsible)](T-015-adaptive-layouts.md) | Etap 5 | queued (after T-014) |
| [T-016 Accessibility semantics](T-016-accessibility.md) | Etap 5 | queued (after T-015) |
| [T-018 Canvas: progress, charts, gradients](T-018-canvas-charts.md) | Etap 5 | queued (after T-016) |
| [T-019 Gestures and visual effects](T-019-gestures-effects.md) | Etap 5 | queued (after T-018) |
| [T-017 Stateful components (tabs, toggles, visibility)](T-017-stateful-components.md) | Etap 10 | queued (after T-019) |
| [T-020 Dynamic values (clock, light/dark, conditions)](T-020-dynamic-values.md) | Etap 7/10 | queued (after T-017) |
| [T-021 Native components via RC `Custom` (text input)](T-021-native-components.md) | Etap 10 | needs design detail (spike first) |
| [T-022 Advanced RC features (templates, drag, sensors)](T-022-advanced-rc-features.md) | Etap 10 | needs design detail (pick by real use case) |
| [T-005 iOS spike](T-005-ios-spike.md) | Etap 3 | blocked: needs Xcode on the dev machine |
| [T-007 Admin panel](T-007-admin-panel.md) | Etap 8 | needs design detail (storage/auth decided: ADR 0016; open review notes inside) |
| [T-010 White-label app export](T-010-app-export.md) | Etap 9 | needs design detail (after T-007, T-011; SDK distribution decision) |

Rows are in priority order (owner, 2026-11-07): fastest path to Milestone 1 (example weather app).
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
`blocked: reason` · `needs design detail` · `done` (+ commit hash once committed).
When a task is done, the next `queued` task becomes `ready`.

## Working a task

1. Set its status to `in progress` here.
2. Follow the task file; when reality differs from the plan, update the task file first, then code.
3. Finish with the [Definition of Done](../../AGENTS.md#definition-of-done-every-task) and
   `./scripts/verify.sh`.
4. Mark it `done`, set the next task to `ready`, tick the README roadmap boxes, and make sure the
   next task is specified well enough to start (files, acceptance criteria) — a fresh agent must be
   able to continue without asking. Commit only when the owner asks (AGENTS.md rule 7).

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
