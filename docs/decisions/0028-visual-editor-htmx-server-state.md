# 0028 — Visual editor: HTMX, editor state on the server, forms from the model's descriptors

- Status: Accepted (owner, 2026-11-23)
- Date: 2026-11-23
- Confirms: [0012](0012-admin-ui-technology.md) for the visual editor (T-029 C1; [tech stack](../../specs/tech-stack.md) asked for this ADR)

## Context

The visual editor (T-029) lets a non-developer build screens without JSON: a component tree,
property forms, later a palette (add, move, nest). Options: keep server-rendered pages + HTMX
(ADR 0012); HTMX with vanilla-JS islands holding the editor state in the browser; or a JS
framework SPA with npm and a build step. Every component must stay editable automatically when
one is added (ADR 0017 point 4), and validation must be the one `problems()`.

## Decision

- **HTMX, state on the server.** The screen being edited is the stored draft (`ScreenStore`).
  Every edit (property form, add, delete, move) is a small request that changes the draft JSON on
  the server, validates the whole screen and answers with re-rendered HTML fragments (tree,
  panel, problems). The JSON editor and the visual editor edit the same draft, so they stay in
  sync by construction.
- **Forms come from the model itself:** the kotlinx.serialization `SerialDescriptor`s of
  `ScreenDefinition` / `Component` (field names, kinds, enum values, nullability, nested and
  polymorphic types) drive a generic form engine in `server-admin`. Only types with custom
  serializers get explicit handling (`Dimension`: number/`fill`/`wrap`; anything unknown: a
  lenient JSON-value field). Labels and help text are an optional map keyed by field name. A test
  walks every `Component` subtype and fails when one has a field the engine cannot edit.
- **No npm, no build step.** A small script from a CDN with an integrity hash is allowed for one
  interaction that HTMX cannot do (e.g. drag and drop), never as the editor's state holder.

## Consequences

- Each click is a server round trip (fine on a VPS; not offline). Undo is not in the browser:
  history/rollback (T-007) and "reload the draft" cover it at first.
- A new component or field needs no editor code; a new custom serializer needs one form case.
- Revisit with evidence (a concrete interaction HTMX cannot reach), in a new ADR.

## How to verify it still holds

```bash
git ls-files | grep -E "package\.json|node_modules|webpack|vite\.config" && echo VIOLATION || echo ok
./gradlew :server-admin:test --tests '*FormSchemaTest*'   # every component field is editable
```
