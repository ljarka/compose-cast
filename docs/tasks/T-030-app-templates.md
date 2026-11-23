# T-030 — App templates (whole apps, not single screens)

Status: ready · Roadmap: Etap 8 · Depends on: T-007, T-029, T-031 · Module: app-templates, server-admin

## Goal
On first login (and in "New app"), the admin picks a ready app: **News**, **Product catalog**,
**Events**, **Weather**, **Blank**. One click creates all screens, data sources, lookups, brand and
navigation, which work immediately with keyless demo APIs. The user then edits them.

## Why
An empty panel is the biggest barrier for non-developers. A working app in 5 minutes sells the
product (owner, 2026-11-16).

## Design
- A template = a bundle in a new whole-app bundle format (defined here; reused for app export/import) (screens `<id>.json`, `brand.json`,
  `manifest.json` with name, description, preview image, required secrets). Built in under
  `server-app/src/main/resources/templates/<name>/`.
- Applying a template is an import into the database as **drafts** (nothing goes live until the user
  publishes). Id conflicts: prefix or ask.
- Every template must pass `ScreenDefinition.problems()` and render in `compat-tests` (one
  screenshot per template home screen).
- Required secrets from the manifest are shown as a checklist ("set `COMPOSECAST_SECRET_X` in
  `.env`").
- Demo APIs only keyless and stable (Open-Meteo, TVMaze, a static JSON in the template).

## Acceptance criteria
- 4 templates and Blank; each applies in an empty database and plays on the emulator.
- Test: every bundled template validates; a screenshot of each home screen.

## Docs to update
README (Etap 8), new playbook "add a template", `server-app/AGENTS.md`, history.
