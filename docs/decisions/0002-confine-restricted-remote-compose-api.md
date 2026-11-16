# 0002 — Confine the restricted Remote Compose API

- Status: Accepted
- Date: 2026-11-07

## Context
In RC alpha20–alpha23 the whole creation API (`createRcBuffer`, `RcScope`, `RemoteComposeWriter`)
and player API (`RemoteComposePlayer`, `RemoteDocument`) are `@RestrictTo(LIBRARY_GROUP)`. There
is no public alternative. Signatures and semantics change between alphas (see 0004).

## Decision
- Creation API is used **only** in `sdk-server/.../RemoteComposeRenderer.kt` (+ tests).
- Player API is used **only** in `sdk-android`.
- No RC type appears in a public API of compose-cast (`ScreenDocument` wraps `RemoteDocument`
  internally).
- Lint `RestrictedApi` is a warning only in `sdk-android`; it stays an error elsewhere.

## Consequences
An RC upgrade touches two places. Our DSL (`ScreenBuilder`) is the stable API for screen authors.

### Risk note (2026-11-16)
The project depends on an API Google does not promise to keep public-usable. If a release removes
or hides the creation API: (1) stay pinned to the last working alpha (the player ships inside each
app, so pinned apps keep working); (2) check whether a stable public API exists by then;
(3) otherwise write a superseding ADR for ADR 0001. Containment: thanks to this ADR and ADR 0015,
only `RemoteComposeRenderer.kt` and `sdk-android` would have to change. `./scripts/check-rc-release.sh`
is the early warning.

## How to verify it still holds
`grep -rl "androidx.compose.remote" --include=*.kt */src/main` lists only the renderer (server)
and `sdk-android` files.
