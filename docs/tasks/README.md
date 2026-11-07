# Task queue

**If you are an agent without a specific request from the owner: take the first task below whose
status is `ready`, read its file and do exactly that.** Tasks implement the
[README roadmap](../../README.md#roadmap) in dependency order.

| Task | Roadmap | Status |
|---|---|---|
| [T-001 Typed actions](T-001-typed-actions.md) | Etap 2 | ready |
| [T-002 Serializable screen model + JSON screens](T-002-screen-model-json.md) | Etap 2 | ready (after T-001) |
| [T-003 Per-request rendering, route params, ETag](T-003-per-request-rendering.md) | Etap 2 | ready (after T-002) |
| [T-004 Version negotiation](T-004-version-negotiation.md) | Etap 2 | ready (after T-003) |
| [T-005 iOS spike](T-005-ios-spike.md) | Etap 3 | blocked: needs Xcode on the dev machine |
| [T-006 Data sources](T-006-data-sources.md) | Etap 4 | needs design detail (after T-003) |

Statuses: `ready` · `ready (after T-xxx)` · `in progress` · `blocked: reason` · `needs design detail`
· `done (commit)`.

## Working a task

1. Set its status to `in progress` here (commit with the work).
2. Follow the task file; when reality differs from the plan, update the task file first, then code.
3. Finish with the [Definition of Done](../../AGENTS.md#definition-of-done-every-task) and
   `./scripts/verify.sh`.
4. Mark it `done (<commit>)`, tick the README roadmap boxes, and make sure the **next** task is
   specified well enough to start (add files, acceptance criteria) — a fresh agent must be able to
   continue without asking.

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
