# 0001 — Remote Compose is the Android wire format and renderer

- Status: Accepted
- Date: 2026-11-07

## Context
The project started (commits up to `2f2103c`) as JSON `ScreenModel` + a hand-written Compose
renderer, with Remote Compose dependencies declared but unused. The owner considered this a
fundamental mistake: with a custom renderer every new component needs an app release, which is
exactly what Remote Compose (RC) avoids — the player renders whatever operations a document
contains.

## Decision
On Android the server sends RC binary documents (`application/octet-stream`) and the app plays them
with the RC player. compose-cast never draws Android UI itself. The legacy JSON models were deleted.

## Consequences
- New visual capabilities are added on the server (renderer/DSL), not in the app.
- RC is alpha (`1.0.0-alpha23` now); its behavior per version is tracked in
  [`docs/remote-compose.md`](../remote-compose.md).
- iOS is not covered by RC (no official player) — see [0007](0007-ios-strategy-spike.md).

## How to verify it still holds
`curl -s -D - -o /dev/null localhost:8080/screens/main` → `Content-Type: application/octet-stream`;
`compat-tests` screenshots are produced by `RemoteComposePlayer`.
