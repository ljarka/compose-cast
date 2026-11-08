# 0008 — External APIs are bound on the server (data sources)

- Status: Accepted (2026-11-08, format designed in [T-006](../tasks/T-006-data-sources.md))
- Date: 2026-11-07

## Context
Components must be able to show data from arbitrary HTTP APIs (weather, movies, …); the APIs are
not part of the project. Screens must be configurable from the admin panel, without code.
RC documents are rendered on the server; on-device logic is limited to RC expressions/actions.

## Decision
Backend-for-frontend binding: a screen declares **data sources** (HTTP request template with path/
query params and secrets from env, JSON response). Components bind to fields (`{{source.path}}`),
lists repeat an item template over an array. The server fetches, renders the RC document with the
data, and serves it. Actions navigate with parameters (`navigate:movie?id=42`) or reload.
Same definition format for the DSL and the admin panel.

Format (2026-11-08, details and grammar in T-006): screen fields `params` (with defaults), `data`
(name → `{url, headers, timeoutMs, cacheSeconds, optional}`), `lookups` (value tables), `error`
(components shown when a source fails, else HTTP 502); `{{path | filter:arg}}` templates in string
fields with a closed filter set (`round`, `date`, `lookup`, `default`, `upper`, `lower`); a `repeat`
component over an array or over **zipped parallel arrays**; `{{secret.NAME}}` =
`COMPOSECAST_SECRET_NAME`, only in source URLs/headers. Binding is a model-to-model transform
before rendering, so renderers never see templates.

## Consequences
- API keys never reach the app; works identically for any client renderer (RC, DivKit).
- Every screen render may call external APIs → per-request rendering, caching per source,
  timeouts, error/empty states are required.
- Interactions needing no server (toggles, tabs) use RC local state later.

## How to verify it still holds
`examples/demo/screens/data.json` renders live data; `compat-tests` renders it with fixture data;
`grep -r "secret" ` in `/screens/*/debug` output never shows a secret value (T-006 tests).
