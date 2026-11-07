# T-016 — Accessibility semantics

Status: queued (after T-015) · Roadmap: Etap 5 · Depends on: T-012

## Goal

TalkBack reads compose-cast screens correctly: content descriptions, roles, state descriptions,
enabled/clickable flags — set automatically where we know them, overridable in the model.

## Why

RC alpha23 has `Modifier.semantics { contentDescription, role(AccessibleComponent.Role), text,
stateDescription, enabled, clickable }` (`RcScope.kt` ~l.2018, `RcScopeImpl` l.2362). Apps built on
compose-cast must be accessible (store review, legal requirements).

## Design

- Defaults in the renderer: `Image.alt` → contentDescription; buttons and any component with
  `modifier.action` → role BUTTON + clickable; headings (`h1…h3`) → heading if the role enum has it
  (check `AccessibleComponent.Role` values in `remote-core`).
- `modifier.semantics { description, role, stateDescription, hidden }` overrides; `hidden: true`
  for decorative images (check whether RC can mark a node as not important; if not, document).
- Validation: clickable image without `alt` or description → **warning** list (not an error) exposed
  via `/screens/{id}/debug` — decide in the task, record in `protocol/AGENTS.md`.

## Steps

Read `AccessibleComponent` + semantics ops in `remote-core` and how `remote-player-view` exposes
them to Android accessibility (virtual view hierarchy?) → model → renderer → tests (`describe()` has
semantics ops) → **emulator with TalkBack** (`adb shell settings put secure
enabled_accessibility_services …` or manual) → record results.

## Acceptance criteria

- TalkBack on the emulator announces image descriptions, "button" for buttons, and skips hidden
  decorative images on the demo screens.
- `./scripts/verify.sh` green.

## Docs to update

`docs/remote-compose.md` (what the View player exposes to accessibility, version),
`protocol/AGENTS.md`, `sdk-server/AGENTS.md`, README Etap 5 accessibility box, `docs/history.md`.

## Out of scope

Localization of descriptions (Etap 7 locale task), focus order customization (if RC lacks it).
