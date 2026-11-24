# T-032 — Live preview on a phone (instant refresh while editing)

Status: done · Roadmap: G3 · Depends on: T-007, T-047, ADR 0013, ADR 0029 · Module: server-admin, sdk-android, app-shell

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

## Result (2026-11-24, [ADR 0030](../decisions/0030-live-draft-previews.md))
- One preview id per admin user and screen (`PreviewScreens.open`), updated in place by every
  saved draft (JSON editor and visual editor → `ScreenPublishing.updatePreview`), served live
  every 500 ms; a new registry is always a full frame (edited texts re-measured); 30 min sliding
  lifetime, logout revokes. The Preview app needed no stream code (T-047): title "Preview · …",
  no refresh button on live screens.
- Pairing/token: the unguessable preview id is the token (ADR 0027); bound to the admin user,
  revoked on logout, expires 30 min after the last save. No "Stop preview" button yet.
- Measured on the emulator: edit saved → phone shows it 1.2 s after the save started;
  `scripts/verify-live-preview.sh` (curl + adb + Maestro `preview-shows.yaml`) checks it in < 3 s;
  logout closed the phone's stream at once.

## Acceptance criteria
- Edit a text in the panel → the emulator updates in under 2 s. A Maestro flow plus a server test
  for the event stream cover it.
- A draft is never served without a valid token (test).

## Docs to update
New ADR (supersedes or extends 0013), `sdk-android/AGENTS.md`, `app-shell`, playbook, history.
