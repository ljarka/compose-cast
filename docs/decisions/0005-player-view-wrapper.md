# 0005 — Wrap `remote-player-view` instead of `remote-player-compose`

- Status: Accepted
- Date: 2026-11-07

## Context
`remote-player-compose` (`RemoteDocumentPlayer`) is a thin `AndroidView` wrapper around the same
view player, but pulls appcompat and Compose 1.11 (alpha20). It is also `@RestrictTo`.

## Decision
`sdk-android/ComposeCastScreen` hosts `RemoteComposePlayer` in our own `AndroidView` (~30 lines),
reloads only when the document changes and routes host actions via `addActionCallback`.

## Consequences
Fewer dependencies; we own theme handling. Reconsider when the Compose player brings real value
(e.g. a non-View renderer).

## How to verify it still holds
`sdk-android/build.gradle.kts` depends on `remote-player-view`, not `remote-player-compose`.
