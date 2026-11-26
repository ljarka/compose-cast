# T-057 — Component designer: draw custom components on an artboard

Status: in progress (step 1–3 done, branch `feat/component-designer`) · Roadmap: G6 (owner 2026-11-26) · Depends on: T-049, T-048, ADR 0033 · Module: protocol, server-admin, server-app (demo), compat-tests

## Goal
An owner draws a custom component (shapes, gradients, vector icons, texts, tap areas) on an
artboard in the panel, with no Remote Compose knowledge, and uses it on screens like any custom
component. Decision: [ADR 0034](../decisions/0034-component-designer.md).

## Why
Owner, 2026-11-26: "I cannot create any meaningful component" on `/admin/components` (raw RC JSON
only). Asked for "a new, more detailed editor for components based on drawing on a canvas, not the
same as arranging components in a tree".

## Result so far (2026-11-26)
1. **Model + compiler** (`protocol/ComponentDesign.kt`, `ComponentDesignTest`):
   - `design` on `ComponentDefinition`, compiled to the RC JSON body on every decode and stored
     without it;
   - demo `promo-card` on the `custom` screen (golden looked at);
   - `CustomComponentTapTest`: only the button runs the action.
   - Found: RC `offset` moves drawing, not touch; a click covers padding (remote-compose.md → RC
     JSON 14–15).
2. **Admin** (`ComponentPages.kt`, `DesignerPagesTest`):
   - `GET/POST /admin/components/{id}/design` (JSON in, `{saved, problems}` out),
     `/admin/components/designer.js`;
   - "Start with: drawing" on new components; links between the JSON editor and the designer.
3. **Designer** (`server-admin/src/main/resources/admin-assets/designer.js`, `designer.ftl`):
   - tools: select/move/resize (Shift keeps the ratio), rect, ellipse, line, pen (straight
     segments), text, tap area;
   - layers (order, hide, duplicate, delete), properties, component settings and params;
   - undo/redo, keyboard shortcuts, zoom, debounced auto-save, phone image refresh;
   - SVG import with path normalization.
   - Checked in headless Chrome (render, draw, move, import of a Lucide and a Material icon) and on
     the real player through the local preview renderer.

## Next steps
4. **Not yet tried by the owner in a real browser session**: do that first, then fix what they hit.
5. Multi-select + align/distribute; groups; snapping to other elements' edges; copy/paste.
6. Pen with Bézier handles; editing path points; radial gradient; dashed strokes (`pathEffect`).
7. Images on the artboard (needs the image pipeline in components, ADR 0033 out of scope); built-in
   components (gauge, button) as elements.
8. Bind numbers to params (e.g. a progress bar's width from `{{params.value}}`).
9. Component gallery of drawn examples ("Start from an example"), and the AI writing designs (T-033):
   the design format is a far easier target for a model than raw RC JSON.

## Acceptance criteria
- An owner creates a card with a gradient, an imported icon, a param title and a button that
  navigates, publishes it and uses it on a screen. The phone shows it like the designer.
- Invalid input (bad color, unknown action, missing param) is refused with a path; nothing is saved.
- `./scripts/verify.sh` green.

## Out of scope
Animations, interactions between elements (screen values), text input.
