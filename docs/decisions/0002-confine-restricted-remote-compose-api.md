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

## How to verify it still holds
`grep -rl "androidx.compose.remote" --include=*.kt */src/main` lists only the renderer (server)
and `sdk-android` files.
