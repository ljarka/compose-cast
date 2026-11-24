# T-032 — Live preview on a phone (instant refresh while editing)

Status: ready · Roadmap: G3 · Depends on: T-007, T-047, ADR 0013, ADR 0029 · Module: server-admin, sdk-android, app-shell

## Goal
A phone running the app in **preview mode** shows the screen being edited in the panel and
refreshes within about 1 s of every change, without scanning a QR code again.

## Why
The "edit → see" loop decides whether the editor is pleasant. ADR 0013 (QR + `__preview`) is the
one-shot version.

## Design
- Pairing: the panel shows a QR code once (server URL plus a short-lived preview token). The app
  (debug build or a hidden gesture in release builds, decide in the task) stores the pairing.
- Transport: **decided in [ADR 0029](../decisions/0029-live-screens-websocket-data-updates.md)** —
  the T-047 live WebSocket (`/screens/{id}/live`) with the draft's preview id (ADR 0027); a draft
  edit triggers a render instead of a timer (full document on a structural edit, data update
  otherwise). No separate stream endpoint.
- The token is bound to an admin session, expires (e.g. after 12 h) and is revocable in the panel.
- The app shows a "PREVIEW" badge; actions navigate inside the drafts.

## Starting point (after T-047, 2026-11-24)
What exists: `/screens/{id}/live` streams any registry screen that has `live`; `LiveFrames` asks
`screens.registryFor(id)` every tick, and `registryFor` already resolves `__preview-…` draft ids
(ADR 0027) to the preview registry. So the likely shape: (1) a draft preview registry entry the
panel **updates in place** on every save (today a preview is a new id per click); (2) preview
documents marked live (server-side, without changing the stored draft: e.g. a `Live` added when
the preview registry is built); (3) the Preview app opens the stream like any live screen —
`ComposeCastScreen(live = …)` already applies full frames when the structure changes (an edit
usually changes structure). Decide in the task how the stable preview id is bound to the admin
session (token / expiry) and record it in an ADR amending 0027.

## Acceptance criteria
- Edit a text in the panel → the emulator updates in under 2 s. A Maestro flow plus a server test
  for the event stream cover it.
- A draft is never served without a valid token (test).

## Docs to update
New ADR (supersedes or extends 0013), `sdk-android/AGENTS.md`, `app-shell`, playbook, history.
