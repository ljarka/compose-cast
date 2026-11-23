# T-029 — Visual screen editor in the admin panel (no JSON)

Status: done (C1–C3) · Roadmap: Etap 8 · Depends on: T-007 (admin MVP), ADR 0015, ADR 0013 · Module: server-admin, protocol (component schema)

## Goal

Anyone, including people who are not developers, can build an app's screens in the browser. They
add components from a palette, edit properties in forms, set up data sources and bindings, preview
on a phone, and publish. JSON stays available as an "advanced" view of the same model.

## Why

Owner (2026-11-16): the product is a project where anyone creates the screens of their mobile app
in an admin panel. ADR 0017 point 4: everything app-specific must be editable there.

## Design (detail before starting; decide in the task)

- **Schema-driven forms:** generate the property forms from one component schema (types, enums,
  defaults, help text) exported from `protocol` (e.g. JSON Schema generated from `ScreenDefinition`
  or a hand-written descriptor checked by a test), so that every new component is automatically
  editable (ADR 0017 point 4). A test fails when a component lacks a schema entry.
- **Tree editor:** an outline of the component tree (add, move, delete, duplicate) plus a property
  panel. Drag and drop is optional; up/down buttons are enough at first.
- **Preview:** the server renders the draft; the browser shows a screenshot-like preview (decide:
  a server-side render to PNG with the compat-tests pipeline is not possible on a JVM without
  Android, so options are the QR preview on a device (ADR 0013), the `/debug` tree, or a web RC
  player if one matures). Record the decision as an ADR.
- **Data sources:** form for URL/headers/cache. "Test request" shows the JSON response as a tree.
  Clicking a field inserts `{{source.path}}`. Filters are picked from a list.
- **Templates:** starter screens (list, detail, form, onboarding) for a quick start.
- Admin UI technology: ADR 0012.

## Steps (roadmap C1–C3)

1. **C1** ADR on the UI technology. **Done (2026-11-23): [ADR 0028](../decisions/0028-visual-editor-htmx-server-state.md)**
   — HTMX, state on the server (the stored draft), forms from `SerialDescriptor`s.
2. **C2** Component tree + schema-driven property forms for existing screens. Done when: every
   property of every component is editable in a form (test over all `Component` subtypes); the
   JSON editor shows the same draft.

   **C2 result (2026-11-23, `ad259d6`):** `server-admin` `FormSchema.kt` (forms from `SerialDescriptor`s),
   `ScreenTree.kt`, `VisualPages.kt`, `visual.ftl` + `_form.ftl`; link "Visual editor" on the JSON
   editor page. Tests: `FormSchemaTest` (all 15 component types; every component of
   `examples/demo` → form → back unchanged), `VisualPagesTest` (8, incl. every node of every demo
   screen opens and saves unchanged). Looked at with headless Chrome screenshots (button with
   navigate action, nested layouts, screen with data sources). Maps (`params`, `data`, `lookups`,
   action params) and the repeat source are JSON-value fields for now — the data source form is
   T-031. Notes for C3: add/remove/move/duplicate are operations on `ScreenTree` paths (insert into
   a component list, remove, swap with neighbour); a new component starts as the smallest valid
   JSON of its type (required fields: text "", label "", url "", action navigate main, columns 2,
   items "" …) — derive it from `FormSchema` (non-optional fields) and check it with a test over all
   types; after a structural change select the new/moved node.
3. **C3** Palette: add, remove, reorder, nest. Done when: a screen is built from empty without JSON
   (the weather `main` from T-025 is the acceptance test).

   **C3 result (2026-11-23, `69010e0`):** `Palette.kt` (starters, add/delete/duplicate/move/into/out/wrap),
   `Bindings.kt` (alias rename), `POST …/visual/edit`, list selection in the tree, toolbar in
   `visual.ftl`; playbook [build-screen-in-admin.md](../playbooks/build-screen-in-admin.md).
   **Acceptance met by a test:** `BuildFromEmptyTest` rebuilds the weather app's `main`
   (fixture from ljarka/weather-app `f0f729b`) and every demo screen from empty, over HTTP, with
   only the editor's operations — byte-identical. The screen's maps (data sources, params,
   lookups) are still JSON fields: T-031 gives data sources a form. Not done: a human
   non-developer trying it (owner), drag and drop (ADR 0028: buttons first).

## Acceptance criteria

- A non-developer test: build the weather `main` screen from T-025 without touching JSON.
- Every component in `protocol` has a schema entry (test). `./scripts/verify.sh` is green.

## Docs to update

README (Etap 8), `docs/playbooks/` (new: "build a screen in the admin panel"), ADR for the preview
decision, history.
