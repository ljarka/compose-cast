# 0024 — Preview in a generic "compose-cast Preview" app (amends 0013)

- Status: Accepted (owner, 2026-11-23)
- Date: 2026-11-23
- Amends: [0013](0013-admin-preview.md) (which app shows the preview; the `__preview` mechanics stay)

## Context

ADR 0013 previews a draft by scanning a QR code with the owner's own app build. The primary user is
a non-developer ([specs/mission.md](../../specs/mission.md)): before the first release of their app
they have no build to scan with, and producing one needs a toolchain. The success criterion "first
own screen on a phone in under 30 minutes" cannot be met that way. A browser-side RC player does
not exist; rendering previews on the server needs an RC player on the JVM (today only Robolectric
in tests).

## Decision

- One generic Android app, **compose-cast Preview**, built from `app-template` by this repository's
  release workflow and attached to every GitHub Release (APK; Google Play later if useful).
- The admin panel shows a QR code with the server URL (and, for drafts, the `__preview` screen of
  the session, ADR 0013). Scanning it in the Preview app connects the app to that server; the app
  remembers the server.
- The owner's own app is unaffected: it is still configured at build time (`compose-cast.properties`).

## Consequences

- `app-shell` needs a runtime-configurable server URL (QR scan) for the Preview flavor only;
  branded apps keep the build-time URL.
- The Preview app must accept http to the owner's server during setup (before HTTPS); limited to
  the Preview app, never to branded release builds.
- Roadmap steps B1–B3 ([specs/roadmap.md](../../specs/roadmap.md)).

## How to verify it still holds

A fresh install: install the Preview APK from the latest release, scan the admin QR code, the
owner's screen appears — without building anything.
