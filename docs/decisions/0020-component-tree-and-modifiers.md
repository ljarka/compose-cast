# 0020 — Component tree and a fixed box-model modifier

- Status: Accepted (T-012)
- Date: 2026-11-12

## Context

Screens were a flat list rendered into one scrolling column. Real screens (the weather app,
Milestone 1) need rows, columns, boxes and per-component layout/decoration. The screen model is the
product ([ADR 0015](0015-screen-model-is-the-product.md)): JSON files and the admin panel edit it,
so its shape must be simple to author and stable, not a mirror of Compose's API.

Remote Compose alpha23 (verified in `remote-core` sources, see `docs/remote-compose.md`):
all `padding` modifiers are **summed into the content inset**, the **first** width/height modifier
wins, and the size **includes** the padding (exact width = total width; wrap = content + padding).
Decorators (clip, background, border) are painted in order, each inset by the paddings before it.
`weight` exists only in a Row/Column scope.

## Decision

1. **Containers** `column`, `row`, `box` with `children`; alignment/arrangement enums
   (`start|center|end`, arrangement adds `spaceBetween|spaceAround|spaceEvenly` — exactly RC's
   `RcColumnVerticalPositioning`/`RcRowHorizontalPositioning`), plus `spacing` (dp between children,
   rendered as spacers: RC has no `spacedBy`).
2. **One `modifier` object on every component** (not on `repeat`, which expands in place), all
   fields optional, **unordered** — a fixed CSS-like box model instead of Compose's ordered chain:
   `width`/`height` (dp | `"fill"` | `"wrap"`) and `weight` include `padding`; `background`, `border`
   and the clip (`cornerRadius` or `shape: "circle"`) cover the whole size; `padding` insets the
   content; `action` makes the whole size tappable. **No margin** — use the parent's `spacing` or
   padding. Rendered as the RC chain weight → width → height → clip → background → border → click →
   component styling → padding (`RemoteComposeRenderer.boxModel`).
3. **Defaults keep existing screens identical:** children of a column (and the screen) fill the
   width, children of a row wrap; heights wrap. A `spacer` is space along its parent's direction.
4. **Validation at load** with JSON paths: `weight` only for direct children of a row/column (a
   repeat's template counts as its parent's children), max nesting depth 32, colors, negative sizes,
   `cornerRadius` + `shape` together, actions like buttons' (`components[0].children[1].modifier.weight: …`).
5. Only `modifier.action` is bindable (`{{ }}`); colors and sizes are not data.

## Consequences

- One mental model for JSON authors and the admin panel; order-dependent effects of Compose
  (background before/after padding) cannot be expressed. If a real need appears (e.g. margin), add a
  field, do not switch to an ordered list.
- Every new component gets `override val modifier` and goes through `boxModel`
  ([add-component playbook](../playbooks/add-component.md)).
- Images without a height size for the full screen width; inside rows they need a height (aspect
  and crop: T-013).
- Workaround: `shape: circle` is a rounded clip with an oversized radius (alpha23 drops
  `CircleShape` when writing `ClipModifier`).

## How to verify it still holds

`LayoutTest` (protocol), `RemoteComposeRendererTest` (ops in the document), `JsonScreensTest`
(DSL = JSON byte-identical), golden `compat-tests/src/test/screenshots/layouts.png`,
`maestro test app-template/maestro/layouts.yaml`.
