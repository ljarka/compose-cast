# T-036 — Push notifications from the panel (FCM)

Status: queued (after T-035) · Roadmap: Etap 12 · Depends on: T-007, T-024 (plugin) · Module: server-push, android-push, gradle-plugins

## Goal
The admin sends a push notification (title, text, optionally an image) to all users or to a
topic, now or at a set time. Tapping it opens a chosen screen with parameters.

## Why
App owners ask for it very often, and it makes the product look complete (owner, 2026-11-16).

## Design (verify the current FCM HTTP v1 API and Firebase Android setup; never guess)
- App repository: `google-services.json` is added next to `compose-cast.properties`. The
  `io.composecast.app` plugin applies the Google services setup only when the file exists, so push
  is optional. `app-shell` registers the token and asks for notification permission on Android 13+
  (when to ask is configurable).
- Device registers `POST /devices {token, appVersion, locale, topics}`; tokens are stored in the
  server's database.
- Server: Firebase service-account JSON as a secret (`COMPOSECAST_SECRET_FCM_SERVICE_ACCOUNT` or a
  file path); sends with FCM HTTP v1; a scheduler for planned sends; send history in the panel.
- Payload: `action` in the wire format of `Action` (e.g. `navigate:show?id=42`), routed by the
  existing action router on tap.

## Acceptance criteria
- Emulator with Google Play services: a push sent from the panel arrives and opens the screen.
  Server tests use a fake FCM client.
- An app without `google-services.json` builds and runs unchanged.

## Docs to update
Playbook "enable push", `gradle-plugins` and `app-shell` docs, README, history.
