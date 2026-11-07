# T-019 — Gestures and visual effects

Status: queued (after T-018) · Roadmap: Etap 5 · Depends on: T-012

## Goal

More than one tap: long-press and double-tap actions, ripple feedback, haptics, and visual effects
(alpha, scale, rotation, translation, offset, z-order).

## Why

RC alpha23: `Modifier.onLongClick`, `onDoubleClick`, `onTouchDown/Up/Cancel`, `ripple()`,
`performHaptic(RcHaptic)`, `graphicsLayer { scaleX/Y, alpha, rotationZ, translationX/Y }`, `offset`,
`zIndex`, `drawWithContent` (`docs/remote-compose.md` → Procedural catalog).

## Design

- `modifier.longPressAction`, `modifier.doubleTapAction` (same `Action` type as `action`).
- `modifier.ripple: true` — default **on** for anything with an action (check the look on the View
  player first; buttons today have no press feedback).
- `modifier.haptic: "click" | "longPress" | …` (map to the `RcHaptic` constants that exist) fired
  with the action.
- `modifier.alpha`, `rotation` (deg), `scale`, `translate {x, y}` (dp), `offset {x, y}`, `zIndex`.

## Steps

Model → DSL → renderer → tests (`describe()` has `Ripple`, `GraphicsLayer`, `HostNamedAction`
under long-click) → demo `effects` (+ JSON) → screenshot → emulator: long-press and double-tap
reach the action router (`adb logcat -s ComposeCast`), haptic felt/logged.

## Acceptance criteria

- Long-press and double-tap dispatch distinct actions; tap still works on the same component.
- Ripple visible on buttons; effects render as in the golden.
- `./scripts/verify.sh` green.

## Docs to update

`docs/remote-compose.md` (gesture/ripple behavior on the View player), `protocol/AGENTS.md`,
`sdk-server/AGENTS.md`, README Etap 5, `docs/history.md`.

## Out of scope

Drag/swipe gestures with continuous values (`addTouch` — T-022), animations tied to state (T-017).
