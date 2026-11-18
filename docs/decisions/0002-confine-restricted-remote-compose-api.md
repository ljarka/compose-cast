# 0002 — Confine the restricted Remote Compose API

- Status: Accepted
- Date: 2026-11-07

## Context
In RC alpha20–alpha23 the whole creation API (`createRcBuffer`, `RcScope`, `RemoteComposeWriter`)
and player API (`RemoteComposePlayer`, `RemoteDocument`) are `@RestrictTo(LIBRARY_GROUP)`. There
is no public alternative. Signatures and semantics change between alphas (see 0004).

## Decision
- Creation API is used **only** in the package `sdk-server/.../sdk/render/` (+ tests); entry point
  `RemoteComposeRenderer`, everything there `internal`. (Amended 2026-11-18, T-039: was the single file
  `RemoteComposeRenderer.kt`, split for small agent contexts; the boundary is unchanged.)
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
only the `render` package and `sdk-android` would have to change. `./scripts/check-rc-release.sh`
is the early warning.

## How to verify it still holds
Rule R1 in `scripts/check-architecture.sh` (run by `verify.sh`): RC imports only in
`sdk-server/src/main/kotlin/io/composecast/server/sdk/render/*.kt` and `sdk-android/src/main/`.
Manually: `grep -rl "androidx.compose.remote" --include=*.kt */src/main` lists only those files.
