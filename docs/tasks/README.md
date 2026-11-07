# Task queue

**If you are an agent without a specific request from the owner: take the task below whose status
is exactly `ready` (there is at most one), read its file and do exactly that.** Tasks implement the
[README roadmap](../../README.md#roadmap) in dependency order.

| Task | Roadmap | Status |
|---|---|---|
| [T-001 Typed actions](T-001-typed-actions.md) | Etap 2 | done (`4bce343`) |
| [T-002 Serializable screen model + JSON screens](T-002-screen-model-json.md) | Etap 2 | done (`2918209`) |
| [T-003 Per-request rendering, route params, ETag](T-003-per-request-rendering.md) | Etap 2 | ready |
| [T-009 Backend in one command (Docker)](T-009-docker.md) | Etap 6 | queued (after T-003) |
| [T-006 Data sources](T-006-data-sources.md) | Etap 4 | needs design detail — design it right after T-003 (critical path to Milestone 1) |
| [T-008 Local state spike](T-008-local-state-spike.md) | Etap 5/10 | queued (after T-003); RC API verified, PoC remains |
| [T-004 Version negotiation](T-004-version-negotiation.md) | Etap 7 | queued (after Milestone 1, Etap 6) — no installed apps before that; the app already sends its player version |
| [T-005 iOS spike](T-005-ios-spike.md) | Etap 3 | blocked: needs Xcode on the dev machine |
| [T-007 Admin panel](T-007-admin-panel.md) | Etap 8 | needs design detail (ADRs 0011–0014 first; after T-002) |

Rows are in priority order (owner, 2026-11-07): fastest path to Milestone 1 (weather app).

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
