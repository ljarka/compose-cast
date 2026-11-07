# T-015 — Adaptive layouts: FitBox, collapsible row/column, size constraints

Status: queued (after T-014) · Roadmap: Etap 5 · Depends on: T-012

## Goal

Screens that adapt to phone/tablet/foldable widths without the server knowing the device size.

## Why

RC alpha23 has `FitBox` (shows the first child that fits), `CollapsibleRow/Column` with
`horizontal/verticalCollapsiblePriority` (drop low-priority children when space runs out),
`widthIn/heightIn`, `requiredWidthIn/HeightIn`, `fillParentMax*`, `wrapContent*` (see
`docs/remote-compose.md`). Sizing happens on the player, so one document fits every screen.

## Design

- `Component.FitBox(children, contentAlignment)` — `"type": "fitBox"`; children = alternatives
  from largest to smallest. **First verify** the `FitBoxLayout` selection rule in `remote-core`
  sources and document it.
- `collapsible: true` on row/column → `CollapsibleRow/Column`; `modifier.collapsePriority` (float)
  on children.
- Modifier additions: `minWidth/maxWidth/minHeight/maxHeight` (→ `widthIn/heightIn`),
  `fillFraction` for `fillMaxWidth(fraction)`.

## Steps

Read `FitBoxLayout.java`, `CollapsibleRowLayout.java`, `CollapsiblePriority.java` first → model →
DSL → renderer → tests → demo screen `adaptive` (+ JSON) → screenshot tests at **two widths**
(Roborazzi device qualifiers, e.g. phone and tablet) → emulator rotation check.

## Acceptance criteria

- Same document: a 3-item row shows 3 items on a tablet width, fewer on a phone width (lowest
  priority dropped first); FitBox picks the large variant on tablet, small on phone.
- Two screenshot goldens reviewed; `./scripts/verify.sh` green.

## Docs to update

`docs/remote-compose.md` (FitBox/Collapsible rules with version), `protocol/AGENTS.md`,
`sdk-server/AGENTS.md`, README Etap 5, `docs/history.md`.

## Out of scope

Server-side breakpoints from client headers (Etap 7 `RenderContext`), `windowWidth()` expressions
(T-020).
