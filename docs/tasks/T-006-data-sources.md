# T-006 — Data sources (external APIs)

Status: needs design detail — design right after T-003 (critical path) · Roadmap: Etap 4 · Depends on: T-002, T-003

## Goal
Screens show data from external HTTP APIs, configured in the screen definition (no code), per
[ADR 0008](../decisions/0008-server-side-data-sources.md). First consumer: the weather app on
[Open-Meteo](https://open-meteo.com) (Etap 6, no API key).

## Before starting: finish the design in this file
Decide and write down (then set status `ready`):
- Definition format: `dataSources: { weather: { url: "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m", timeoutMs, cacheSeconds } }`.
- Binding syntax in component fields: `{{weather.current.temperature_2m}}`; formatting (units).
- Lists: a `list` component with `items: "{{weather.daily.time}}"` + item template — or arrays of
  objects only? (Open-Meteo returns parallel arrays; TVMaze returns arrays of objects.)
- Secrets: `{{env.TMDB_KEY}}` resolved only on the server.
- Errors: timeout/HTTP error → error state component; empty → empty state.

## Acceptance criteria (draft)
- A weather screen renders real Open-Meteo data (manual check) and fixture data (tests with a fake
  HTTP client, no network in CI).
- Screenshot test of the weather screen with fixture data.
