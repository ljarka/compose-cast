# T-038 — Feature flags and A/B tests on screen elements

Status: queued (after T-032) · Roadmap: Etap 12 · Depends on: T-007, T-029; results need T-037 · Module: server-flags, protocol, sdk-server

## Goal
In the panel, the admin can:
- **Feature flags:** turn individual elements, or whole screens, on and off without publishing a new
  screen version, optionally only for part of the users (percentage, app version, platform,
  locale, logged-in users from T-035).
- **A/B tests:** define 2+ variants of an element or a screen with a traffic split; each device
  sees a stable variant; results come from analytics (T-037).

## Why
Owner (2026-11-16). The server renders per request, so flags and variants are resolved on the
server and the app needs no changes.

## Design
- Model: any component gets an optional `visibleIf: { flag: "new_banner" }` and a component
  `variants: { experiment: "cta_test", variants: { a: [...], b: [...] } }`. Flags and
  experiments are stored separately from screens (tables `flags`, `experiments`) so they can be
  toggled **without a new screen version**.
- Bucketing: a stable anonymous device id (header `X-Composecast-Device`, the same one as T-037) →
  `hash(experiment + deviceId) % 100`. Deterministic and tested.
- Rendering: resolved in the binder step (like data bindings, so renderers never see flags). The
  render cache key includes the resolved flag/variant set; ETag stays correct because it hashes
  bytes.
- Exposure: the server records which variant was served (`experiment_exposure`, T-037).
- Panel: a flag list with toggle/rollout percentage; an experiment editor; results (conversion =
  a chosen `track` event or action per variant).

## Acceptance criteria
- Toggling a flag in the panel hides the element on the next request (no publish). 50/50 split
  verified statistically in a unit test (10,000 ids). Screenshot tests for both variants.

## Docs to update
New ADR (flags and experiments model), `protocol/AGENTS.md`, README (Etap 12 back from Later),
history.
