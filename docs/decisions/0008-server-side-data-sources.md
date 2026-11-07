# 0008 — External APIs are bound on the server (data sources)

- Status: Proposed
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

## Consequences
- API keys never reach the app; works identically for any client renderer (RC, DivKit).
- Every screen render may call external APIs → per-request rendering, caching per source,
  timeouts, error/empty states are required.
- Interactions needing no server (toggles, tabs) use RC local state later.

## How to verify it still holds
To be defined with the implementation (sample apps on Open-Meteo / TVMaze render real data).
