# 0031 — App bundle format (templates now, export/import later)

- Status: Accepted
- Date: 2026-11-24

## Context

T-030 needs whole apps the admin applies in one click: several screens, their data sources,
lookups, navigation between them and a brand. T-010 (export) will need to write an app out and
read it back. Screens already have a canonical JSON form (ADR 0015); the brand has one too.

## Decision

A **bundle** is a directory:

```
<id>/manifest.json        {"name", "description", "home", "screens": [ids in order],
                           "secrets": [{"name","why"}], "fixtures": [{"match","file"}]}
<id>/brand.json           optional BrandConfig
<id>/screens/<screen>.json  one ScreenDefinition per file (ScreenJson)
<id>/fixtures/*.json      optional synthetic API responses for tests/offline previews
```

- Bundled templates live in the `app-templates` module (`resources/templates/`, listed by
  `templates/index.json`) — a module per ADR 0023, resources and a loader only.
- **Applying = importing as drafts** (`server-admin`): nothing goes live until published; the
  brand is applied only when the user keeps "use its colors" checked. Id clashes: the user picks a
  prefix; `AppTemplate.withPrefix` renames screens and every `navigate` to them.
- A bundle is valid when every screen passes `problems()`, `home` is one of its screens, and every
  navigation stays inside the bundle (`AppTemplate.problems()`).
- Required secrets are listed for the user (`COMPOSECAST_SECRET_<NAME>`), never stored in a bundle.

## Consequences

- Templates are plain JSON a non-developer could write; tests render each with its fixtures.
- Export (T-010) can write the same layout; import = the same apply code path.
- Fixtures are only for tests and the panel's offline preview; the app never sees them.

## How to verify it still holds

`AppTemplatesTest` (every bundle valid, builds, renders with fixtures; prefix rename),
`TemplatesScreenshotTest` (home screen goldens), `TemplatesPagesTest` in server-admin (apply as drafts).
