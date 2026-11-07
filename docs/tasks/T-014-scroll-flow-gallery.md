# T-014 — Scrolling, Flow layout, galleries and grids

Status: queued (after T-004) · Roadmap: Etap 5 · Depends on: T-012

## Goal

Horizontal galleries, wrapping chip/tag layouts and grids, and scrolling on any container.

## Why

RC alpha23 has `Modifier.horizontalScroll()`, `verticalScroll(position)` and a `Flow` layout
(`maxItemsInEachRow`, `maxLines`, `weight` in `RcFlowScope`) — see `docs/remote-compose.md`. There
are no lazy lists, but documents are rendered per request, so lists of known size are fine.

## Design

- `modifier.scroll: "vertical" | "horizontal"` on column/row (the root column already scrolls
  vertically — check nested vertical scroll behavior on the player and document it).
- `Component.Flow(children, horizontalArrangement, verticalAlignment, maxItemsInEachRow, maxLines)`,
  JSON `"type": "flow"`.
- `Component.Grid(columns, children, spacing)` = rows of `weight(1f)` cells generated server-side
  (or `Flow` with `maxItemsInEachRow` + weight — pick the one that renders equal-width cells and
  record why).
- `gallery` is just `row` + `scroll: "horizontal"`; add a DSL helper only.
- Repeat-over-data (lists from API arrays) is **T-006** (Etap 4); this task covers static children.

## Steps

Model + validation (`maxItemsInEachRow ≥ 1`, `columns` 1…12) → DSL → renderer → tests → demo screen
`collections` (+ JSON) → screenshot → emulator (scroll by swipe, check no clipping of the last
item).

## Acceptance criteria

- A horizontally scrolling gallery of 10 images scrolls on the emulator; the screen still scrolls
  vertically.
- Flow of 12 chips wraps; a 2- and 3-column grid has equal cell widths.
- `./scripts/verify.sh` green, golden reviewed.

## Docs to update

`protocol/AGENTS.md`, `sdk-server/AGENTS.md`, README Etap 5 boxes, `docs/remote-compose.md`
(nested scroll behavior, Flow behavior), `docs/history.md`.

## Out of scope

Lazy loading / pagination (needs host round-trips; revisit after T-006), pager/snap (no RC op).
