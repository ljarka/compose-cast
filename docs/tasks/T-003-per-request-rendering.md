# T-003 — Per-request rendering, route params, ETag

Status: done · Roadmap: Etap 2 · Depends on: T-002

## Outcome (2026-11-07)
Implemented per the "Design change" section below; `./scripts/verify.sh` green.
- Server: `dynamicScreen`, `RenderContext`, `ScreenParameterException` (→ 400), `LiveScreens`
  (swappable registry), ETag = SHA-256 of bytes + `Cache-Control: no-cache`, 304; thread-safe
  bounded `ImageCache` (failures cached 60 s); `HttpImageLoader` http/https only, ≤ 5 MB.
- SDK: `getScreen(id, params)` (URL-encoded), in-memory ETag cache (LRU 32, fresh decode on 304).
- App: back stack of `Action.Navigate` saved via `ActionCodec` strings.
- Demo: dynamic `echo` screen + "Navigate with params" button on `actions` (golden re-recorded,
  `echo` golden added; both looked at).
- Tests: `DynamicScreensTest` (incl. 50 threads), `ImageCacheTest`, `RoutingTest`
  (`testApplication`: 304, non-ASCII params, 400/404, 50 concurrent requests, registry swap).
- Emulator: main → onboarding → actions → echo (Polish text) → "Again, louder" (`!` appended);
  Back restores the previous echo; reload/back revalidate with `304 Not Modified`; rotation keeps
  the back stack with params; no `FATAL EXCEPTION`.
- Not done (moved to README Etap 7): locale in `RenderContext`.

## Design change (2026-11-07, before implementation) — params are query parameters, not path patterns
Path patterns (`movie/{id}`) would put `/` into screen ids, but ids name files (`<id>.json`,
ADR 0011), and `ActionCodec` already carries params as a query (`navigate:movie?id=42`). So:
- `GET /screens/{id}?k=v` — ids stay single segments; query params = `RenderContext.params`.
- DSL: `dynamicScreen("movie") { ctx -> text(ctx.param("id")) }` (a separate function: an
  overload of `screen` taking `(RenderContext)` would be ambiguous for `{ }` lambdas). Static
  `screen(id) { }` and JSON screens ignore params and are pre-rendered.
- Missing required param (`ctx.param(name)`) → `ScreenParameterException` → HTTP 400.
- `ETag` = hash of the rendered bytes, so it covers everything rendering reads, automatically.
Items below that mention patterns/`{path...}` are superseded by this section.

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
- **The live registry must be replaceable at runtime** (review 2026-11-07): admin publish (T-007)
  and data-dir reloads will swap screens while the server runs. Since the review, `main()` builds
  the registry and passes it to `Application.module(registry)` → `configureRouting(registry)`;
  keep that injection, but route through something swappable (e.g. an `AtomicReference` holder or
  a `ScreenSource` interface) instead of baking an immutable map into routing.
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
  `images` map in `RemoteComposeRenderer` is not thread-safe. **Do not cache failures forever**
  (today a failed fetch is stored as `null` for the life of the registry → a transient network
  error leaves a placeholder until restart): cache failures with a short TTL (~1 min). Limit the
  download size in `HttpImageLoader` (e.g. 5 MB; today unbounded) and allow only http/https —
  admin-entered URLs (T-007) make this a security boundary.
- `ETag` = strong hash of the bytes; `If-None-Match` → `304`. Debug stays at `…/debug`
  (reserve `debug` as last path segment).
- SDK: `getScreen(screen, params)` — URL-encode path segments and query values (today the id is
  pasted into the URL raw); Ktor client sends `If-None-Match` when it has a cached copy
  (in-memory cache is enough here; disk cache is Etap 7).
- App: back stack entries become `(screen, params)`.

## Steps (files to touch)
1. `sdk-server`: `ScreenRegistry.kt` (patterns, `RenderContext`, `render(path, ctx)`, static
   screens pre-rendered for validation), `ScreenDsl.kt` (context-receiving `screen` overload),
   `RemoteComposeRenderer.kt` (thread-safe bounded image cache); tests.
2. `server-app`: `Main.kt` / `Routing.kt` (swappable registry, `/screens/{path...}`, `debug`
   suffix, ETag/304; StatusPages already turns exceptions into logged JSON 500s), `Screens.kt`
   (`echo/{word}` demo screen — DSL only, so no JSON copy; `JsonScreensTest` compares static
   screens only), new `RoutingTest` with Ktor `testApplication { application { module(registry) } }` — add `ktor-server-test-host` to
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
