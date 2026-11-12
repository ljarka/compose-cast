# T-012 — Layout containers and common modifiers

Status: ready · Roadmap: Etap 5 · Depends on: T-002 (screen model)

## Goal

Screens become trees: `column`, `row` and `box` containers with children, and a common set of
modifiers usable on **every** component (padding, size, fill, weight, background, rounded clip,
border, alignment, click action). This is the foundation every other component task (T-013…T-020)
builds on, and the weather app (Milestone 1) needs it.

## Why

Today `ScreenDefinition.components` is a flat list rendered into one scrolling `Column`
(`RemoteComposeRenderer.render`). Real screens need side-by-side and stacked layouts. RC has all
primitives (`docs/remote-compose.md` → Procedural catalog). The model is the product
([ADR 0015](../decisions/0015-screen-model-is-the-product.md)), so the nesting and modifier shape
is a decision → write **ADR 0020**.

## Design (decide in ADR 0020, this is the proposal)

- `Component.Column(children, modifier, horizontalAlignment, verticalArrangement)`,
  `Component.Row(children, modifier, horizontalArrangement, verticalAlignment)`,
  `Component.Box(children, modifier, contentAlignment)`. JSON `"type": "column" | "row" | "box"`,
  `"children": [...]`.
- `modifier` = one serializable data class `Modifier` (name it `Style` if `Modifier` clashes in the
  renderer) with optional fields, default empty, added to **all** components (Text, Image, Button,
  Spacer too):
  `padding` (all or `{start, top, end, bottom}`, dp), `width`/`height` (dp | `"fill"` | `"wrap"`),
  `weight` (only valid inside row/column — validate), `background` (`#RRGGBB`/`#AARRGGBB`),
  `cornerRadius` (dp) or `shape: "circle"`, `border {width, color}`, `action` (makes any component
  clickable = `onClick { hostAction }`, reuse `ActionCodec`).
- Alignment/arrangement enums in JSON: `start|center|end`, `top|center|bottom`,
  arrangement `start|center|end|spaceBetween|spaceAround|spaceEvenly` (check which
  `RcColumnVerticalPositioning`/`RcRowHorizontalPositioning` values exist in `RcPositioning.kt`
  and support exactly those).
- Validation: max nesting depth (e.g. 32), weight outside row/column, bad colors, negative sizes —
  paths like `components[2].children[0].modifier.padding`.
- Existing JSON stays valid (all new fields have defaults).

## Steps (files to touch)

1. ADR `docs/decisions/0020-component-tree-and-modifiers.md` (+ index in `docs/decisions/README.md`).
2. `protocol/.../Screen.kt`: containers, `Modifier`, enums, recursive `problems()`; `ScreenJsonTest`.
3. `sdk-server/.../ScreenDsl.kt`: `column { }`, `row { }`, `box { }` builders (nested
   `ScreenBuilder`), `modifier = Modifier(...)` parameter on every function.
4. `sdk-server/.../RemoteComposeRenderer.kt`: recursive `renderNode`; map `Modifier` →
   RC `Modifier` chain (order: clickable → padding outer? decide and document — Compose order
   matters: background before padding = padded content on colored area).
5. `RemoteComposeRendererTest`: `describe()` contains `ColumnLayout`/`RowLayout`/`BoxLayout`,
   `Padding`, `Background`, `RoundedClipRect`, `Border`, weight.
6. Demo: new screen `layouts` in `demoScreens()` + `examples/demo/screens/layouts.json`;
   screenshot test in `compat-tests`; link from `main`.
7. Emulator check ([verify-on-emulator.md](../playbooks/verify-on-emulator.md)).

## Acceptance criteria

- A row of two weighted cards (rounded, bordered, background) with an image + text column inside
  renders identically from DSL and JSON; screenshot golden reviewed.
- Any component with `modifier.action` triggers the app's action router.
- Invalid weight/depth/color fails at server start with a JSON path.
- `./scripts/verify.sh` green.

## Docs to update

ADR 0020, `protocol/AGENTS.md` (JSON format), `sdk-server/AGENTS.md` (usage), README "Defining
screens" + Etap 5 box, `docs/playbooks/add-component.md` (modifier on new components),
`docs/remote-compose.md` (any behavior found, e.g. modifier order), `docs/history.md`.

## Out of scope

Text/image styling (T-013), scrolling rows and Flow (T-014), adaptive layouts (T-015),
semantics (T-016), gestures beyond click (T-019).
