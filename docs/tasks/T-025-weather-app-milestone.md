# T-025 — Milestone 1: example weather app (external repository) + measurements

Status: queued (after T-026) · Roadmap: Etap 6 · Depends on: T-023, T-024 (owner steps below)

## Goal

Build the weather app as a **separate repository** that only uses published compose-cast
artifacts ([ADR 0017](../decisions/0017-apps-in-own-repositories.md)). It proves the product
promise end to end and shows the framework's real gaps. Do this **before** more catalog work
(T-018…T-022): real gaps beat guessed ones.

## Owner steps (blocking; an agent cannot do these)

1. First GitHub Release `v0.1.0`: the image on GHCR (set the package to public) and the release
   assets (T-023).
2. Reposilite on the VPS plus GitHub secrets so that the release publishes `sdk-android`,
   `app-shell` and the plugin (T-024).
3. A domain for HTTPS (Caddy profile).

If they are not done, the agent prepares the repository locally against
`./scripts/verify-app-repository.sh` (local Maven) and records it in the status line as
`in progress (waiting for owner: release)`.

## Design

App repository layout (not in this repository; create it next to it, e.g. `../weather-app`, and ask
the owner where to push it):

```
server/  docker-compose.yml + example.env (from the release), data/screens/*.json, data/brand.json
android/ settings.gradle.kts, build.gradle.kts (plugin io.composecast.app), compose-cast.properties, icon
README.md
```

Screens (Open-Meteo, no key): `main` (current conditions plus 3 days), `hourly` (24 h, with a
`repeat` list or a T-018 chart if it is already done), `daily` (7 days), `cities` (a static list of
cities → `navigate:main?lat=…&lon=…&city=…`).

## Steps

1. Write the screens using only existing components. **Every missing feature goes in a gap list**,
   not into app code.
2. For each gap: create a framework task here (generic, ADR 0017 point 3) and put it in the queue.
   Small gaps can be fixed inline in this repository with a screenshot test.
3. Measure on the VPS or locally with Docker: p50/p95 `GET /screens/main` latency (cold and warm
   source cache), document size in bytes, server RSS after 1,000 requests. Use `hey` or `ab`, or a
   simple loop with `curl -w`.
4. Run the app on the emulator against the server; take screenshots.

## Acceptance criteria

- The weather app repository builds with `./gradlew assembleRelease` with no Kotlin sources and
  talks to an `https://` server.
- The README Etap 6 boxes are ticked. Screenshots and measurements are in this README
  ("Screenshots" and a new "Performance" note).
- The gap list is turned into tasks in `docs/tasks/README.md`.

## Docs to update

README (Etap 6, screenshots, measurements), `docs/history.md`, `docs/playbooks/new-app-repository.md`
(fix every step that was wrong), task queue.

## Out of scope

Admin panel, Play Store publishing.
