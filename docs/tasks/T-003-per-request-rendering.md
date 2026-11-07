# T-003 — Per-request rendering, route params, ETag

Status: ready · Roadmap: Etap 2 · Depends on: T-002

## Goal
Render documents per request instead of once at startup, with route parameters
(`/screens/movie/42` → screen `movie/{id}` with `id=42`) and HTTP caching via `ETag`.

## Why
Data screens (T-006) and personalization need a screen to be a function of the request. Today
`ScreenRegistry` pre-renders at start (and fetches images once).

## Design
**Starting point after T-002:** `RegistryBuilder` collects validated `ScreenDefinition`s (DSL via
`ScreenBuilder.build()`, JSON via `screensFrom`) and renders each once in `build()`;
`RemoteScreen(definition, bytes)`. The renderer is `RemoteComposeRenderer.render(ScreenDefinition)`.

- **Where params enter (decided here):** a parameterized screen is a *function* producing a
  `ScreenDefinition`: DSL `screen("movie/{id}") { ctx -> text("Movie ${ctx.params["id"]}") }`
  (keep the existing no-arg `screen(id) { }` source-compatible). The registry stores the builder
  lambda and runs it per request, then validates (`definition.validate()`) and renders. JSON
  screens stay static until T-006 adds bindings (`{{params.id}}` in JSON is T-006 scope, not here).
  The definition's `id` for a rendered pattern is the pattern (`movie/{id}`).
- Screen ids may be patterns: `movie/{id}`. `GET /screens/{path...}` matches the pattern and
  passes `params` (path + query) to rendering. Navigation actions (T-001) carry params, the SDK
  sends them as path/query.
- `ScreenRegistry.render(path, RenderContext(params, …)): RenderedScreen(bytes, title, etag)`.
  Design `RenderContext` to grow: README Etap 12 (targeting, A/B, feature flags) will add client
  attributes (app version, platform, country, plan from headers/JWT) — the ETag/cache key must
  include whatever the rendering reads.
  Static screens (no params) are still rendered once at startup **for validation** (fail fast) and
  may be cached; parameterized screens render per request.
- Image cache: thread-safe, bounded (e.g. LRU 256 entries) shared across renders — the current
  `images` map in `RemoteComposeRenderer` is not thread-safe.
- `ETag` = strong hash of the bytes; `If-None-Match` → `304`. Debug stays at `…/debug`
  (reserve `debug` as last path segment).
- SDK: `getScreen(screen, params)`; Ktor client sends `If-None-Match` when it has a cached copy
  (in-memory cache is enough here; disk cache is Etap 7).
- App: back stack entries become `(screen, params)`.

## Steps (files to touch)
1. `sdk-server`: `ScreenRegistry.kt` (patterns, `RenderContext`, `render(path, ctx)`, static
   screens pre-rendered for validation), `ScreenDsl.kt` (context-receiving `screen` overload),
   `RemoteComposeRenderer.kt` (thread-safe bounded image cache); tests.
2. `server-app`: `Routing.kt` (`/screens/{path...}`, `debug` suffix, ETag/304), `Screens.kt`
   (`echo/{word}` demo screen — DSL only, so no JSON copy; `JsonScreensTest` compares static
   screens only), new `RoutingTest` with Ktor `testApplication` — add `ktor-server-test-host` to
   `gradle/libs.versions.toml` (rule R7) as `testImplementation`.
3. `sdk-android`: `ComposeCastClient.getScreen(screen, params)` + in-memory ETag cache.
4. `app-template`: back stack of `(screen, params)` (must stay `rememberSaveable` — use a
   `Saver` or a parcelable/serializable entry); `Navigate` passes `action.params`.
5. `compat-tests`: unchanged goldens; optionally an `echo` screenshot.

## Acceptance criteria
- `GET /screens/main` twice → second with `If-None-Match` returns 304 (test with Ktor
  `testApplication`).
- A parameterized demo screen (e.g. `echo/{word}` showing the word) renders `echo/hello` and
  `echo/zażółć` correctly; unknown path → 404 JSON.
- Concurrent renders (e.g. 50 parallel requests in a test) succeed.
- Screenshots unchanged; emulator navigation works.

## Verification
`./scripts/verify.sh`; [verify-on-emulator.md](../playbooks/verify-on-emulator.md) (navigate,
back, reload; logcat shows `If-None-Match` / `304` on reload).

## Docs to update
`server-app/AGENTS.md` (endpoints, params, ETag), `sdk-server/AGENTS.md`, `sdk-android/AGENTS.md`,
ADR if caching policy is a decision, `docs/history.md`.
