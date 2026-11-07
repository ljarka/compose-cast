# 0007 — iOS: decide by spike between RC player and DivKit

- Status: Proposed (spike pending)
- Date: 2026-11-07

## Context
Goal: apps whose whole UI is server-driven, published for **Android and iOS**. Verified on Google
Maven (2026-11-07): `androidx.compose.remote` has no iOS / Kotlin Multiplatform artifacts; the
official player is Android-only. Community project `camaelon/remotecompose-experiments`
(Apache-2.0) has a C++ RC engine with an iOS SwiftUI viewer, a TypeScript player (browser) and a
Compose Desktop player, self-described as an experimental sandbox not supported by Google.
DivKit has mature Android/iOS/web players for its own JSON format.

## Decision (owner, 2026-11-07)
Build the server core format-agnostic (screen model, data sources, admin panel) and run a short
spike on iOS comparing:
- **(a)** the community RC engine/SwiftUI viewer playing our documents;
- **(b)** DivKit's iOS player fed from the same screen model (second renderer on the server).

Criteria: fidelity vs Android screenshots, actions/navigation, images, text scaling, effort to
integrate, maintenance risk (RC alpha format churn vs two renderers in parity).

## Consequences
- The internal screen model must stay independent of RC (RC is a renderer/adapter).
- Requires Xcode (not installed on the dev machine as of 2026-11-07).
- Outcome is recorded by superseding this ADR.

## How to verify it still holds
Status stays Proposed until a spike report is linked here.
