# T-045 — Generic "compose-cast Preview" app + draft preview

Status: ready · Roadmap: specs/roadmap.md B1–B3 · Depends on: T-007 (admin panel, `__preview`) · Module: app-shell, app-template, server-admin

## Goal

A non-developer sees their own screen on a phone before their app exists: install the Preview APK
from the latest GitHub Release, scan the admin's QR code, done ([ADR 0024](../decisions/0024-generic-preview-app.md)).

## Steps (one roadmap step each)

1. **B1** Preview flavor of `app-template`: server URL from a QR scan (remembered), http allowed only
   in this flavor; the release workflow attaches `compose-cast-preview.apk` to every release.
   Done when: scanning a QR with a server URL shows that server's `main` screen.
2. **B2** Draft preview (ADR 0013 mechanics): `POST /admin/api/screens/{id}/preview` → `__preview`
   per session; the editor shows the QR. Done when: a saved draft is visible in the Preview app
   before publishing.
3. **B3** Measure "first screen in under 30 minutes" on a fresh VPS following only the docs; fix the
   docs until it holds. Done when: the time is written into `specs/roadmap.md` and the history.

Notes from T-007 for B2: add the preview route inside `loggedIn { }` in `server-admin`'s
`adminRoutes` (POST → editors only, covered by `AdminPanelTest`'s route walk); render the draft
without touching the database or `LiveScreens` (a separate one-screen registry built like
`AdminScreens.build`, served under `__preview`); the editor template is `screen.ftl`.

## Open questions (decide in B1)

QR scanning library vs. the system camera + deep link (`composecast://connect?url=…`); prefer the
deep link (no camera permission, no dependency) if the system camera opens it reliably.
