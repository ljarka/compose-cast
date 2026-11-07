# T-003 — Per-request rendering, route params, ETag

Status: ready (after T-002) · Roadmap: Etap 2 · Depends on: T-002

## Goal
Render documents per request instead of once at startup, with route parameters
(`/screens/movie/42` → screen `movie/{id}` with `id=42`) and HTTP caching via `ETag`.

## Why
Data screens (T-006) and personalization need a screen to be a function of the request. Today
`ScreenRegistry` pre-renders at start (and fetches images once).

## Design
- Screen ids may be patterns: `movie/{id}`. `GET /screens/{path...}` matches the pattern and
  passes `params` (path + query) to rendering. Navigation actions (T-001) carry params, the SDK
  sends them as path/query.
- `ScreenRegistry.render(path, RenderContext(params, …)): RenderedScreen(bytes, title, etag)`.
  Static screens (no params) are still rendered once at startup **for validation** (fail fast) and
  may be cached; parameterized screens render per request.
- Image cache: thread-safe, bounded (e.g. LRU 256 entries) shared across renders — the current
  `images` map in `RemoteComposeRenderer` is not thread-safe.
- `ETag` = strong hash of the bytes; `If-None-Match` → `304`. Debug stays at `…/debug`
  (reserve `debug` as last path segment).
- SDK: `getScreen(screen, params)`; Ktor client sends `If-None-Match` when it has a cached copy
  (in-memory cache is enough here; disk cache is Etap 7).
- App: back stack entries become `(screen, params)`.

## Acceptance criteria
- `GET /screens/main` twice → second with `If-None-Match` returns 304 (test with Ktor
  `testApplication`).
- A parameterized demo screen (e.g. `echo/{word}` showing the word) renders `echo/hello` and
  `echo/zażółć` correctly; unknown path → 404 JSON.
- Concurrent renders (e.g. 50 parallel requests in a test) succeed.
- Screenshots unchanged; emulator navigation works.

## Docs to update
`server-app/AGENTS.md` (endpoints, params, ETag), `sdk-server/AGENTS.md`, `sdk-android/AGENTS.md`,
ADR if caching policy is a decision, `docs/history.md`.
