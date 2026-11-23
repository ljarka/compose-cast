# T-029 — Visual screen editor in the admin panel (no JSON)

Status: queued (after T-045) · Roadmap: Etap 8 · Depends on: T-007 (admin MVP), ADR 0015, ADR 0013 · Module: server-admin, protocol (component schema)

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

## Acceptance criteria

- A non-developer test: build the weather `main` screen from T-025 without touching JSON.
- Every component in `protocol` has a schema entry (test). `./scripts/verify.sh` is green.

## Docs to update

README (Etap 8), `docs/playbooks/` (new: "build a screen in the admin panel"), ADR for the preview
decision, history.
