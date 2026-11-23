# T-045 — Generic "compose-cast Preview" app + draft preview

Status: in progress — next step: B3 (B1, B2 done) · Roadmap: specs/roadmap.md B1–B3 · Depends on: T-007 (admin panel, `__preview`) · Module: app-shell, app-template, server-admin

## Goal

A non-developer sees their own screen on a phone before their app exists: install the Preview APK
from the latest GitHub Release, scan the admin's QR code, done ([ADR 0024](../decisions/0024-generic-preview-app.md)).

## Steps (one roadmap step each)

1. **B1** Preview flavor of `app-template`: server URL from a QR scan (remembered), http allowed only
   in this flavor; the release workflow attaches `compose-cast-preview.apk` to every release.
   Done when: scanning a QR with a server URL shows that server's `main` screen.

   **B1 result (2026-11-23, `b94299d`):** module `app-preview` (not a flavor, see its AGENTS.md); plugin key
   `app.preview`; shell `PreviewConnection.kt` + `ConnectScreen.kt` + `ConnectLink` alias;
   `server-app` `GET /connect` (ADR 0026 decides the open question below: QR → https `/connect`
   page → `intent://` link, no camera permission); release job `preview-apk`. Checked on the
   emulator: fresh app → connect screen; the page's `intent://` link (fired with `am start`) →
   connected → demo `main`; restart keeps the server; Change server → connect screen; only
   `io.composecast.preview` handles the link; demo app smoke flow passes. **Not checked:** the
   tap in Chrome (the emulator ran without `-gpu host`, Chrome drew black; `maestro/connect.yaml`
   is ready) and a real phone camera scan. **Owner:** create the signing secrets
   ([playbook](../playbooks/preview-app.md)) before the next release, or it has no APK.
2. **B2** Draft preview (ADR 0013 mechanics): `POST /admin/api/screens/{id}/preview` → `__preview`
   per session; the editor shows the QR. Done when: a saved draft is visible in the Preview app
   before publishing.

   **B2 result (2026-11-23):** ADR 0027 changes the mechanics: `POST /admin/screens/{id}/preview`
   (the editor's text, not only saved drafts), id `__preview-<32 hex>` per preview, 30 min,
   `PreviewScreens` in `server-app`, inline SVG QR (`io.nayuki:qrcodegen`) of
   `/connect?screen=…`; the app pushes the screen on top of `main`. Tests: `PreviewScreensTest`,
   `AdminScreensTest` (+1), `ScreenPagesTest` (+1), `PreviewLinkTest` (+1). Emulator (admin mode,
   curl as the browser, the page's `intent://` link fired with `am start`): the draft shows
   ("Draft on phone"), `GET /screens/main` still "Home"; rotation keeps it once; back → `main`; a
   second preview scanned with the app open shows the new draft. Not checked: the editor page in a
   browser and a real camera scan (no browser tool; same as B1).
3. **B3** Measure "first screen in under 30 minutes" on a fresh VPS following only the docs; fix the
   docs until it holds. Done when: the time is written into `specs/roadmap.md` and the history.
   **Needs the owner:** a fresh VPS (or one to wipe) with a domain, and the Preview signing secrets
   (B1) so the release has an APK; an agent can follow the docs over SSH and time it, but someone
   has to scan with a real phone at the end.

Notes from T-007 for B2: add the preview route inside `loggedIn { }` in `server-admin`'s
`adminRoutes` (POST → editors only, covered by `AdminPanelTest`'s route walk); render the draft
without touching the database or `LiveScreens` (a separate one-screen registry built like
`AdminScreens.build`, served under `__preview`); the editor template is `screen.ftl`.

## Open questions (decide in B1)

QR scanning library vs. the system camera + deep link (`composecast://connect?url=…`); prefer the
deep link (no camera permission, no dependency) if the system camera opens it reliably.
