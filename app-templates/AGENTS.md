# app-templates — AGENTS.md

Bundled **app templates** (T-030): whole apps — screens, brand, navigation, data sources — that the
admin panel applies as drafts with one click, and the **bundle format** they use
([ADR 0031](../docs/decisions/0031-app-bundle-format.md); app export/import will reuse it).
Applying (drafts in the database, publish all) lives in `server-admin`, not here.

## Scope

This module only: resources + a small loader. Depends on `protocol` only (main code); tests also
use `sdk-server` to build and render every template. No Ktor, no database, no Remote Compose.

## Files

| File | Purpose |
|---|---|
| `AppTemplates.kt` | `TemplateManifest` (`name`, `description`, `home`, `screens`, `secrets`, `fixtures`), `AppTemplate` (`problems()`, `fixture(url)`, `withPrefix(prefix)`), `AppTemplates.all` / `[id]` (loaded from the classpath) |
| `resources/templates/index.json` | Template ids in the order the panel shows them (a jar cannot be listed) |
| `resources/templates/<id>/manifest.json` | Name, description, home screen, screen ids (order), required secrets, test fixtures |
| `resources/templates/<id>/brand.json` | Optional `BrandConfig` (colors) |
| `resources/templates/<id>/screens/<screen>.json` | Screen JSON ([format](../docs/screen-json-format.md)) |
| `resources/templates/<id>/fixtures/*.json` | Synthetic API responses with the real shape: tests and screenshots without network |

## Templates

| Id | Screens | Data | Shows |
|---|---|---|---|
| `news` | `news`, `story?id` | Hacker News via `hn.algolia.com` (keyless) | repeat + cards, kpis, open_url |
| `shop` | `shop`, `product?id` | `fakestoreapi.com` (keyless) | list without images (embedded images are full size until T-027), product image, kpi + rating gauge |
| `events` | `events`, `event?id` | none: lookups keyed by `params.id` | static content the owner edits |
| `weather` | `weather?lat&lon&city` (live, 60 s), `cities` | `open-meteo.com` (keyless) | kpi, humidity gauge, 24 h sparkline, 7 days (zip repeat + `wmo` lookup) |
| `blank` | `home` | none | one screen to start from |

## Rules

- **Keyless, stable APIs only**, images in PNG/JPEG/GIF/BMP (the server cannot embed WebP —
  DummyJSON was rejected for that). Lists without images until T-027.
- Every navigation target must be a screen of the same template (`problems()` checks it);
  `withPrefix` renames screens and the navigations to them when ids clash in the panel.
- Fixtures are **synthetic** (no copied live data), shaped like the real response; a binding that
  finds nothing renders empty and fails `AppTemplatesTest`.
- New template: [playbook](../docs/playbooks/add-template.md).

## Test

```bash
./gradlew :app-templates:test                       # AppTemplatesTest: valid, builds, renders with fixtures, prefix
./gradlew :compat-tests:verifyRoborazziDebug        # TemplatesScreenshotTest: template-<id>.png (home screens)
```
