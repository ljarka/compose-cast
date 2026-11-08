# 0017 — Apps live in their own repositories, built without framework code changes

- Status: Accepted (owner, 2026-11-08); open question resolved by [ADR 0018](0018-distribution-like-immich.md): option (a)
- Date: 2026-11-08

## Context

The owner, when starting T-009: *"This weather app shouldn't require any changes in code. The way
should be using our framework, install backend on VPS and adjust app, but adjusted app should move
to different repository."*

Until now Milestone 1 (README Etap 6) implicitly meant "weather screens in this repo". That would
let weather-specific code creep into the framework and would not prove the product promise: a user
gets compose-cast, deploys it, configures an app — no Kotlin.

## Decision

1. **This repository is the framework only**: protocol, server SDK + server app (Docker image),
   Android SDK, `app-template`, the demo (`examples/demo`), tests and docs. No code or screens for
   a specific product app.
2. **Each real app is its own repository** (first: the weather app, Milestone 1). It contains only
   configuration and content:
   - the server data directory: `screens/<id>.json`, `brand.json`, data-source definitions (T-006);
   - deployment for a VPS: a `docker-compose.yml` that **pulls the published server image** (it does
     not build compose-cast), `.env.example` for secrets, HTTPS reverse proxy;
   - the Android app: Gradle files applying the published `io.composecast.app` plugin,
     `compose-cast.properties` (T-011) and the icon — **no Kotlin** (ADR 0018, T-024).
3. **A missing feature is a framework feature, never app code.** If the weather app needs
   something (a component, a binding, a list), it is added here, generically, with a task, a
   screenshot test and docs — then used from the app repository as configuration.
4. **Everything that differs between apps is configuration editable in the admin panel** (owner,
   2026-11-08: "I don't need to modify backend code to adjust to my app"): screens, data sources,
   lookups, brand, app settings. A feature that only a file or the DSL can express is incomplete
   until the admin panel (T-007) can edit it; JSON files and the admin panel share one model
   (ADR 0015), so a new model field is automatically admin-editable once T-007's editor covers it.
5. The app repository pins a framework version (image tag, SDK version), so framework changes
   reach an app only when it upgrades.

## Open question (resolved 2026-11-08: (a), see ADR 0018)

How does an app repository get the Android shell and SDK?

- **(a) Published artifacts**: publish `protocol` + `sdk-android` (and the shell code of
  `app-template` as a library) to a Maven repository; the app repository has only Gradle files +
  `compose-cast.properties`. Clean upgrades; needs publishing (README Etap 11, pulled forward).
- **(b) Standalone copy**: a script exports `app-template` + vendored `sdk-android`/`protocol`
  sources + version catalog + wrapper into a new repository (what T-010's admin export would do).
  No publishing infra; upgrades = re-export.

Recommendation: (a), with JitPack or GitHub Packages as the first repository — it is also what
T-010 review point 1 calls the long-term answer, and it keeps app repositories free of copied code.

## Consequences

- Publishing the server image (GHCR, multi-arch) moves from Etap 11 into Milestone 1 (T-023);
  T-009 already builds amd64 + arm64.
- HTTPS for the VPS (README Etap 9) is needed for Milestone 1: release builds are https-only (T-011).
- `docker-compose.yml` in this repo stays the **dev/demo** setup (builds from source); the app
  repository's compose file is the production shape (pulls the image).
- T-006 data sources must be fully declarative (definitions in the data directory, secrets from
  environment) — no per-app server code.

## How to verify it still holds

- `grep -ri weather --include=*.kt .` finds nothing outside tests/fixtures of generic features.
- The weather app repository builds and deploys with no diff in `app-template` sources versus the
  pinned framework version.
