# 0012 — Admin panel: web UI technology

- Status: Accepted
- Date: 2026-11-07

## Context

The admin panel needs a browser UI for creating and editing screen definitions. Options:

1. **Server-rendered HTML + HTMX** — Freemarker/Thymeleaf templates served by Ktor; HTMX for
   partial updates without a full page reload. Zero JS build toolchain; backend-only Kotlin dev
   experience; ~3–5 KB JS payload.
2. **Static HTML + vanilla JS** — raw `fetch()` calls, no framework, served as static files from
   Ktor. Simple but verbose; poor UX for a property editor.
3. **SPA (React/Vite in a separate module)** — best UX; requires a Node.js build step, a separate
   `npm` dependency tree, and bundling into the Ktor jar or a CDN. Adds complexity that a
   hobbyist project cannot justify at MVP.

The target user is a developer or designer who can open a browser — not a product-grade CMS. The
MVP needs: screen list, a JSON component editor (property fields), a Publish button, and a
rollback list. HTMX covers all of this with very little code.

## Decision

**Server-rendered Freemarker templates + HTMX** served by the `admin-server` Ktor module.

- Ktor plugin: `io.ktor:ktor-server-freemarker`.
- HTMX loaded from CDN (`unpkg.com/htmx.org`) with an integrity hash; no npm.
- Styling: minimal inline CSS or a single `admin.css` static file; no Tailwind build step.
- The component palette (text, image, button, spacer) rendered as clickable cards that append a
  JSON block to the editor textarea via `hx-swap`.
- A `<textarea>` with `contenteditable` holds the raw JSON for the current screen; Publish POSTs
  it to `/admin/api/screens/{id}`.

If UX requirements grow beyond HTMX's capabilities, write a superseding ADR before adding a
build toolchain.

## Consequences

- No Node.js build step; `./scripts/verify.sh` stays pure Kotlin/Gradle.
- Screen editor is a JSON textarea at MVP — not drag-and-drop; acceptable for developer users.
- Freemarker templates live in `admin-server/src/main/resources/templates/`.
- Static assets (CSS, optional JS) in `admin-server/src/main/resources/static/`.

## How to verify it still holds

```bash
grep -r "node_modules\|webpack\|vite\|react" admin-server/ && echo "VIOLATION" || echo "ok"
```
