# Mission

> Part of the project constitution: **mission** (why) · [tech stack](tech-stack.md) (how) ·
> [roadmap](roadmap.md) (what and when). Read all three before starting a feature; change them
> together when one changes.

## Purpose

compose-cast lets **anyone build and run their own Android app without writing code**: the app's
screens, navigation, data and brand are defined in an admin panel on the owner's own server, and a
thin Android app renders them natively. Changing a screen never needs an app update.

## Target audience

| Who | Role | What they need from us |
|---|---|---|
| **Non-developer app owner** (primary) | Small business, creator, organization that wants its own app | Build screens in a browser, see them on a phone, publish a branded app to Google Play — no Kotlin, no Android Studio |
| Developer / integrator (secondary) | Sets up the server for an owner, writes JSON/DSL screens, extends the catalog, or embeds compose-cast screens/flows in an existing Android app via `sdk-android` | Clear docs, a stable screen model, one-command install, and a standalone Android SDK (`io.composecast:sdk-android`) |
| Contributors and AI agents | Maintain the project long-term | Every decision written down, executable rules, screenshot tests |

Success is measured by the primary user. Developer features are welcome when they also serve the
non-developer path (e.g. JSON screens become admin import/export).

## Problem

Building a native Android app — and every later change to it — needs a developer, an Android
toolchain and a store release. Small owners cannot afford that loop, and existing no-code builders
are hosted platforms: the owner rents the app, cannot self-host it and is locked into a vendor.

## Value proposition

- **No code, no store release for changes:** screens are edited and published in the admin panel;
  apps already installed show the new version on the next load.
- **Native, not a web view:** documents are played by AndroidX Remote Compose.
- **Own it:** open source (Apache 2.0), self-hosted on a cheap VPS with one `docker compose up`;
  the owner's data, keys and app belong to the owner.
- **Real data:** screens bind to any HTTP JSON API on the server; API keys never reach the app.

## Guiding principles

1. **The screen model is the product; wire formats are adapters.** One serializable
   `ScreenDefinition` is written by JSON, DSL and the admin panel alike (ADR 0015). Android uses
   Remote Compose only, never a custom renderer (ADR 0001).
2. **Never break an app in users' hands.** The server renders for the capabilities each client
   reports; an invalid edit is rejected or keeps the previous version live.
3. **Configuration, not code, per app.** Everything that differs between apps (screens, data
   sources, brand, app settings) is configuration, editable in the admin panel; apps live in their
   own repositories (ADR 0017).
4. **One server = one product.** Everyone runs their own server; the install is as simple as
   Immich's (ADR 0018).
5. **Interaction on the device where possible** (RC variables, expressions, states), so the app
   stays fast without a round trip.
6. **Verified, written down.** Every visual change is screenshot-tested and checked on a device;
   every decision is an ADR, so agents and people can maintain the project long-term (ADR 0009).

## Non-goals

- **Multi-tenant SaaS.** We do not run a shared hosted service; there is no account on "our"
  server. One server serves one product.
- **iOS and web clients.** Android only (ADR 0022); no renderers for other platforms.

Open source and self-hosted only: no paid tier, hosted offering or closed modules are planned.

## What success looks like (12 months)

1. **A non-developer ships an app.** Following only the docs, a non-developer goes from an empty
   VPS to a signed app in Google Play without writing code and without installing Android Studio
   (build via the exported repository's GitHub Actions workflow).
2. **First screen in under 30 minutes.** From a fresh install to the owner's first own screen
   visible on their phone (via the generic preview app) in under 30 minutes.

Both are proven by an app built only in the admin panel (roadmap: TV shows app) with the time
measured and written down.
