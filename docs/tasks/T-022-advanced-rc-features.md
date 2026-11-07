# T-022 — Advanced RC features: templates, sensors, drag, particles, sound

Status: needs design detail (pick by real use case) · Roadmap: Etap 10 · Depends on: T-012, T-017

## Goal

Cover the remaining RC alpha23 capabilities so nothing the format can do is out of reach:
reusable templates (macros), drag/swipe with continuous values, device sensors, particle effects,
sound, meshes, bitmap fonts, scheduled refresh.

## Why

Owner request (2026-11-07): support everything RC can do. The APIs exist in `RcScope.kt`
(`defineMacro`/`inflate`, `addTouch`, `accelerometerX…`, `gyroscope…`, `magnetometer…`,
`ambientLightLevel()`, `createParticles`/`particlesLoop`, `addSound`/`playSound`,
`remoteMesh2D…`, `createBitmapFont`, `wakeIn(seconds)`) — see `docs/remote-compose.md` →
Procedural catalog, row "Exotic". Most are niche; each needs a real screen to justify its model
shape (ADR 0015: the model is the product, no speculative fields).

## Sub-items (split into separate tasks when one is picked)

1. **Templates / macros** (Etap 10 "Templates") — reusable server-side components first (model
   level, no RC); `defineMacro` only to shrink documents, measure size gain.
2. **Drag / swipe** — `addTouch` with a float variable: slider, swipe-to-dismiss card, carousel
   snap (check what `addTouch` supports: ranges, stops, velocity).
3. **Sensors** — tilt/parallax effects (`accelerometer*`), compass (`magnetometer*`); check
   permissions/availability on the View player.
4. **`wakeIn`** — periodic re-evaluation (countdowns) vs host-driven reload.
5. **Particles, sound, meshes, bitmap fonts** — demo-only until a use case appears.

## Acceptance criteria (per sub-task)

Verified behavior in `docs/remote-compose.md`, model + renderer + screenshot + emulator check like
every component ([add-component.md](../playbooks/add-component.md)).

## Out of scope

Anything requiring a custom renderer (rule 1).
