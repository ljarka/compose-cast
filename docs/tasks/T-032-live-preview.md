# T-032 — Live preview on a phone (instant refresh while editing)

Status: queued (after T-047) · Roadmap: G3 · Depends on: T-007, T-047, ADR 0013, ADR 0029 · Module: server-admin, sdk-android, app-shell

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

## Acceptance criteria
- Edit a text in the panel → the emulator updates in under 2 s. A Maestro flow plus a server test
  for the event stream cover it.
- A draft is never served without a valid token (test).

## Docs to update
New ADR (supersedes or extends 0013), `sdk-android/AGENTS.md`, `app-shell`, playbook, history.
