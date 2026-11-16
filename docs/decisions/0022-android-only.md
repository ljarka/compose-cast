# 0022 — Android only: iOS is out of scope (supersedes 0007)

- Status: Accepted (owner, 2026-11-16)
- Date: 2026-11-16
- Supersedes: [0007](0007-ios-strategy-spike.md)

## Context

ADR 0007 planned a spike to pick an iOS renderer: (a) a community RC engine, or (b) DivKit's iOS
player with a second server renderer. Remote Compose has no official iOS player. Option (b) would
have meant two renderers kept in parity, and DivKit also has an Android player, which would have
undermined ADR 0001. Every RC-specific interaction feature (T-017, T-020–T-022) would also have had
to be rebuilt for iOS. The spike was blocked (no Xcode).

## Decision

The owner decided on 2026-11-16 that compose-cast targets **Android only**. No iOS shell, SDK or
renderer and no iOS spike. T-005 is dropped.

## Consequences

- Remote Compose is the only client wire format. Interaction features (states, variables,
  expressions, `Custom`) may use RC primitives directly. A second-renderer parity requirement no
  longer blocks them.
- The screen model (`ScreenDefinition`, ADR 0015) stays the product and stays RC-free. The reasons
  are authoring (DSL, JSON, admin panel), validation and data binding, not portability to another
  platform. ADR 0002 (RC API confined to the renderer/SDK) still holds.
- Xcode is not needed. There is no SwiftPM publishing.
- If iOS ever comes back, it needs a new ADR that supersedes this one.

## How to verify it still holds

`grep -rn "iOS" README.md docs/tasks/README.md` shows only references to this decision.
