# 0034 — Component designer: draw components on an artboard, compiled to RC JSON

- Status: Accepted
- Date: 2026-11-26
- Amends: [0033](0033-custom-components-raw-rc-json.md) (a component's body can also be compiled
  from a `design`) and [0012](0012-admin-ui-technology.md)/[0028](0028-visual-editor-htmx-server-state.md)
  (one vanilla JS island in the panel, without a build step).

## Context

Owner, 2026-11-26: on `/admin/components` "I cannot create any meaningful component". The only way
was typing raw Remote Compose JSON. Google does not document that format, the panel had no reference
and no examples, and the AI that ADR 0033 meant to write bodies (T-033) does not exist. The owner
asked for **a separate, more detailed editor for components, based on drawing on a canvas. It is
not the same as arranging components in a tree** (the visual editor, ADR 0028).

Options weighed in the conversation:

1. **A reference and examples for RC JSON** (and later the AI). They help experts and the AI, but
   not someone who wants to draw. This is still worth doing as a follow-up.
2. **A "Drawing" component in the tree editor.** This is still tree arrangement, which the owner
   ruled out.
3. **A free-form designer (Figma-like) with its own saved format, compiled to RC JSON.** The owner
   chose this.

Facts checked first (RC 1.0.0-alpha23 sources and the real player under Robolectric, 2026-11-26):

- The RC JSON parser has `offset`, `padding`, `size`, `clip` and `onClick` modifiers, canvas
  `translate`/`scale`/`rotate`/`save`, `linearGradient`, and expressions over `width`, `height`
  and `density` (`ExpressionParser`).
- **`offset` moves the drawing but not the touch area.** A probe of taps over a grid hit the box's
  un-offset place. **A click covers the component's whole bounds, padding included**, whatever the
  modifier order (docs/remote-compose.md → RC JSON 14–15).
- The DSL `Canvas` can hold children, but a JSON `canvas` drops them (fact 5). Canvas draw
  operations always paint under children.

## Decision

- A `ComponentDefinition` gets an optional **`design`** (`protocol/ComponentDesign.kt`). It has an
  artboard `width` (360 dp), a `height`, `cornerRadius`, and `elements` listed bottom first. Element
  types: `rect`, `ellipse`, `line`, `path` (SVG data, optional `pathWidth/Height` viewBox), `text`,
  `tap`. Each element has x/y/w/h in dp, an **anchor** (`left`/`right`/`center`/`stretch`) for other
  phone widths, a fill (optionally a gradient), a stroke, opacity, rotation, and an optional action.
  Colors, texts and actions take `{{params.x}}`/`{{brand.x}}`.
- **The design is the source; the body is compiled from it** (`ComponentDesign.toBody`, plain JSON
  in `protocol`, no RC API, rule R1 unchanged). `ComponentJson` compiles on every decode and stores
  designed components without the body. Everything after that is ADR 0033 unchanged: whitelist,
  trial parse, publish, re-validation on RC upgrades, the `custom` component on screens.
- **The compiled layout:**
  - a full-width box of `height` dp, clipped by `cornerRadius`;
  - consecutive shapes share one `canvas`. Each shape is inside `save` and goes through
    `translate` (anchor expression in pixels) → `scale(density)` → its own dp box, so every number
    in a design stays dp;
  - texts are boxes between canvases, so z-order holds;
  - elements with an action get tap areas on top. These are an outer box with padding and an inner
    box with the click, because of the offset and click-bounds facts above.
- **The designer** is `/admin/components/{id}/design`: an SVG artboard plus `designer.js`, a vanilla
  JS island served by the admin with no build and no library. It has:
  - tools: select/move/resize, rect, ellipse, line, pen, text, tap area;
  - layers, a properties panel, params, undo/redo, keyboard shortcuts;
  - **SVG import**: paths, rects, circles, lines and polygons become path layers. The path data is
    normalized to the subset RC parses: relative commands, H/V, S/T and packed arc flags are
    converted.
  - Every change is POSTed as JSON (debounced) and checked like the JSON editor; a valid one becomes
    the draft. The phone image (T-048) refreshes after each save.
- New components start as a drawing by default. Raw RC JSON stays available for experts and the AI.

## Consequences

- Owners draw components with no RC knowledge. The SVG preview is close to the result, and the
  phone image is the real player.
- The panel has its first sizeable JS file. It needs care like code: it is checked by
  `DesignerPagesTest` (served, saves) and by hand in a browser, not by unit tests.
- Texts and tap areas cannot start above the top or outside the sides (padding ≥ 0). Shapes can
  bleed out and are clipped.
- A design is not a full vector editor yet. Missing: groups, multi-select, Bézier handles in the pen
  (it draws straight segments; curves come from SVG import), radial gradients, images, and built-in
  components on the artboard. They are follow-ups in T-057.

## How to verify it still holds

- `./gradlew :protocol:test --tests '*ComponentDesignTest*' :server-admin:test --tests '*DesignerPagesTest*'`
- `compat-tests`: the `custom` golden shows the designed `promo-card`;
  `CustomComponentTapTest` checks that only its button runs the action.
- After an RC upgrade: re-check facts 14–15 (offset vs touch, click bounds) before trusting tap
  areas.
