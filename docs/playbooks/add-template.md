# Playbook — add an app template

A template is a whole app the admin applies in one click (T-030, [ADR 0031](../decisions/0031-app-bundle-format.md)).
Module: [`app-templates`](../../app-templates/AGENTS.md).

1. **Pick the data.** Keyless, stable API (or none: static content with lookups, like `events`).
   Check the real response with `curl` (shape, size: data sources stop at 2 MB) and its images: the
   server embeds PNG/JPEG/GIF/BMP only, at full size — no images in long lists until T-027.
2. **Write the bundle** under `app-templates/src/main/resources/templates/<id>/`:
   `manifest.json` (`name`, `description`, `home`, `screens` in order, `secrets` if any,
   `fixtures`), optional `brand.json`, `screens/<screen>.json`. Navigate only to screens of the
   same template; give detail screens a `params` default so they render in the panel.
3. **Fixtures:** `fixtures/*.json` — small **synthetic** responses with the real shape; map them
   in `manifest.json` → `fixtures: [{"match": "<part of the URL>", "file": "fixtures/x.json"}]`.
4. **Register** the id in `templates/index.json` (order = order in the panel) and add it to the
   list in `AppTemplatesTest` (`the four app templates …`).
5. **Test:** `./gradlew :app-templates:test` (valid, builds, renders with fixtures, no empty texts).
6. **Screenshot:** add `@Test fun <id>() = capture("<id>")` to `TemplatesScreenshotTest`,
   `./gradlew :compat-tests:recordRoborazziDebug`, **look at** `template-<id>.png`.
7. **Device:** apply it in the panel (Templates), publish, open it on the emulator with real data
   ([verify-on-emulator](verify-on-emulator.md)).
8. **Docs:** the table in `app-templates/AGENTS.md`, `docs/history.md`.
