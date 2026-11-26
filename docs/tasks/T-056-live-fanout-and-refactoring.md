# T-056 — Live screen fan-out and refactoring of largest files

Status: queued (after T-055) · Roadmap: quality · Depends on: — · Module: server-app, server-admin, app-shell

## Goal

Share live-screen polling across concurrent WebSocket clients watching the same screen, and split
the largest multi-responsibility files (`VisualPages.kt`, `ComposeCastActivity.kt`).

## Why

- `LiveRoute.kt` currently runs an independent polling loop per WebSocket connection; `N` clients
  watching the same live dashboard trigger `N` redundant upstream API calls and `N` renders.
- `VisualPages.kt` (605 lines) mixes routing, view-model mapping, and tree editing;
  `ComposeCastActivity.kt` (394 lines) mixes Activity lifecycle, navigation back stack, and UI.

## Design

- Introduce a `LiveScreenHub` in `server-app` that maintains one shared producer coroutine per
  `(screenId, params, rcApiLevel, rcProfiles)` while at least one subscriber is connected,
  broadcasting frames via `SharedFlow` (and stopping when the last subscriber disconnects).
- Split `VisualPages.kt` into focused files (routes vs. form/tree view-model helpers).
- Extract back-stack and deep-link state logic from `ComposeCastActivity.kt` into a unit-testable
  state holder in `app-shell`.

## Steps (files to touch)

1. `server-app/.../LiveRoute.kt` + `LiveScreenHub.kt` + unit test verifying fan-out (2 clients →
   1 upstream render per tick).
2. `server-admin/.../VisualPages.kt`: split into cohesive files.
3. `app-shell/.../ComposeCastActivity.kt`: extract navigation/back-stack state logic + unit tests.

## Acceptance criteria

- Two concurrent WebSocket clients for the same live screen share a single render loop.
- No main source file in `server-admin` or `app-shell` exceeds ~350 lines.
- `./scripts/verify.sh` is green.

## Verification

`./scripts/verify.sh`

## Docs to update

`server-app/AGENTS.md`, `server-admin/AGENTS.md`, `app-shell/AGENTS.md`, `docs/history.md`.

## Out of scope

Multi-node pub/sub (Redis/NATS).
