# 0027 — Draft previews: one unguessable screen id per preview (amends 0013)

- Status: Accepted
- Date: 2026-11-23
- Amends: [0013](0013-admin-preview.md) (the mechanics: endpoint, id, lifetime, QR format); with
  [0024](0024-generic-preview-app.md) (Preview app) and [0026](0026-preview-connect-page.md) (`/connect`)

## Context

ADR 0013 planned one `GET /screens/__preview` per session, `POST /admin/api/screens/{id}/preview`
returning JSON with a PNG QR code, and a 5-minute lifetime. The admin is now server-rendered pages
(no JSON API yet), several people may preview at once, the QR must lead through `/connect` (0026),
and finding a phone, scanning and installing the Preview app takes longer than 5 minutes.

## Decision

- The editor's **Preview on phone** button posts the textarea to `POST /admin/screens/{id}/preview`
  (editors; checked like a draft). Nothing is saved.
- `server-app` builds a one-screen registry (live brand) whose screen id is
  `__preview-<32 hex chars, SecureRandom>` and keeps it in memory (`PreviewScreens`) for 30
  minutes, at most 100 at a time (oldest out). `GET /screens/__preview-…` serves it like any
  screen; it is never in the database, `/screens` or `LiveScreens.current`, and gone on restart.
- The page shows a QR code (inline SVG, `io.nayuki:qrcodegen`) of
  `<server>/connect?screen=__preview-…`; `/connect` passes `screen` on in the app link and the
  Preview app opens it on top of `main` (back returns to the live screens).

## Consequences

- Anyone with the link sees the draft for 30 minutes — the id is the secret (128 bits), like an
  unlisted link. Do not paste preview links where they should not be read.
- Navigation from a preview goes to live screens (the draft of other screens is not previewed).
- A reserved prefix: real screen ids never start with `__preview-` (admin ids start with a letter
  or digit; a JSON file with such an id would be shadowed).

## How to verify it still holds

`PreviewScreensTest`, `AdminScreensTest` (`a preview is served to apps under its own id …`),
`ScreenPagesTest` (`preview shows a QR code …`); on a device: T-045 B2 result in
[docs/tasks/T-045-preview-app.md](../tasks/T-045-preview-app.md).
