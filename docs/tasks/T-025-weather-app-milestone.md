# T-025 — Milestone 1: example weather app (external repository) + measurements

Status: in progress (waiting for owner: domain) · Roadmap: Etap 6 · Depends on: T-023, T-024 (owner steps below) · Module: external repository (+ fixes in existing modules)

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

## Progress (2026-11-18, agent)

Done locally, without the owner steps:
- Repository `../weather-app` (local git, no remote): `server/` (compose file + `example.env` from
  `distribution/`, `data/brand.json`, `data/screens/{main,hourly,daily,cities}.json`), `android/`
  (`compose-cast.properties` with a placeholder `https://weather.example.com`,
  `compose-cast.local.properties` for debug, wrapper, `maestro/smoke.yaml`), README, screenshots.
- Built against a publish dry run (`-PcomposecastVersion=0.1.0`) via the new
  `scripts/local-maven.init.gradle.kts`; `assembleDebug` and `assembleRelease` (unsigned) green with
  no Kotlin. Server = locally built image tagged `ghcr.io/ljarka/compose-cast-server:local`.
- Emulator: Maestro flow through all screens passed; screenshots in the README.
- Measurements: README "Performance" (p50 3 ms warm / 47 ms cold, docs 3–9 KB, RSS ~260 MB with
  `-Xmx128m`; default heap grows → `JAVA_OPTS` documented).
- Gap list → T-043 (app bar shows the screen id while loading), T-040 (brand surface/text colors),
  T-041 (day/night: lookup by two values), T-042 (data dir needs a restart), T-044 (release
  signing). Fixed inline: playbook steps (debug config via `composecast.appConfig`, wrapper copy,
  Maestro output dir, local Maven), `JAVA_OPTS` docs. Not a gap: Open-Meteo, dates, repeats,
  rows/weights, navigation params, emoji in text all worked as documented.
- Smaller notes (no task): "Today" instead of the weekday needs conditions (T-020); an hourly chart
  would need T-018 — the list is fine.

Remaining (needs the owner): owner steps 1–3, then ask where to push `../weather-app`, set the real
domain in `compose-cast.properties`, deploy `server/` on the VPS (https), run the app against it,
tick the README Etap 6 boxes, set this task `done`.

## Progress (2026-11-23, agent)

- Release `v0.1.0` published with `gh release create` after the owner set the repository
  variable `COMPOSECAST_MAVEN_URL` (it had been a secret; `release.yml` reads `vars.`). Jobs image,
  assets, maven green; `0.1.0` of protocol, sdk-android, app-shell and the plugin on
  `maven.jarka.club`; the GHCR image pulls anonymously (already public).
- `../weather-app` builds from the published artifacts without the init script (`assembleDebug`,
  `assembleRelease`); server from image `v0.1.0`; Maestro flow green on the emulator.

- Repository pushed to https://github.com/ljarka/weather-app (public, created by the owner).
- 2026-11-23: moved to `v0.2.0` (brand `surfaceColor` instead of card backgrounds, night icons via
  T-041); Maestro flow green on the emulator, screenshots refreshed.

Remaining (needs the owner): a domain; then deploy `server/` on
the VPS (https), set the domain in `compose-cast.properties`, run the app against it, tick Etap 6
(T-023 and weather boxes), set this task `done`.

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
