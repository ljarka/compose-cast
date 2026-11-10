# T-006 — Data sources (external APIs) and bindings

Status: done (`c6b0b3a` + docs) · Roadmap: Etap 4 · Depends on: T-002 (screen model), T-003 (per-request rendering)

## Progress (2026-11-08)
Implemented and pushed (verify.sh green): `protocol/Binding.kt` (template parser, `ParamSpec`,
`DataSource`, `RepeatItems`), model fields + `Component.Repeat` + validation in `Screen.kt`;
`sdk-server/DataLoader.kt` (`DataLoader`, `HttpDataLoader`, `DataSourceException`), `Binder.kt`
(`DataFetcher`, `DataCache`, `BoundScreen`, filters), registry/DSL integration; server-app
`Dispatchers.IO` + 502; demo screen `data` (+ JSON, linked from `main`); tests `BindingTest`,
`DataBindingTest`, `RoutingTest`; goldens `data.png`, `data-error.png`, `main.png` (reviewed).
Checked by curl against Docker + live Open-Meteo (Berlin 5°C Overcast; bad lat → error state).
Implementation notes: filter args may also be bare names (`lookup:wmo`); an image whose URL binds
empty is skipped; the DSL is `repeat(path)` / `repeat(zip = mapOf(…), alias = "d")` (`as` is a
Kotlin keyword; JSON keeps `items`/`as`); `ScreenRegistry.definitions()` / `isBound()` added; `JsonScreensTest` can rewrite
examples (`-Pcomposecast.writeExamples=true`).

**Emulator check (2026-11-11, step 5):** image rebuilt from `c6b0b3a`, `docker compose up`, app on
`Medium_Phone_API_37.0`: Home → "Live data" shows live Open-Meteo (Berlin 3°C Overcast, 3-day
repeat over zipped parallel arrays); "Warsaw" navigates with params (1°C, title Warsaw); back pops
Warsaw → Berlin → Home; second request 4 ms (source cache), then `304`; `?lat=999` → `error`
components with Retry; no `FATAL EXCEPTION`. Docs updated (ADR 0008, module AGENTS.md, README,
history). **Done.**

## Goal
A JSON screen (no Kotlin) declares HTTP data sources and binds component fields to the responses;
the server fetches, binds and renders per request
([ADR 0008](../decisions/0008-server-side-data-sources.md), accepted with this design).
First real consumer: the weather app on [Open-Meteo](https://open-meteo.com), **in its own
repository** ([ADR 0017](../decisions/0017-apps-in-own-repositories.md)): nothing
Open-Meteo-specific goes into Kotlin, tests here use fixtures.

## Why
Milestone 1 needs live data. Owner rules (2026-11-08): an app never needs backend code changes, and
**everything app-specific is configurable in the admin panel** (T-007). So the whole feature is
part of the serializable screen model ([ADR 0015](../decisions/0015-screen-model-is-the-product.md)):
what a JSON file can say, the admin panel can edit. Secrets come from the container environment
(the admin panel may later manage them, T-007).

## Design

### Example (the format this task implements)
```json
{
  "id": "forecast",
  "title": "{{params.name}}",
  "params": { "lat": { "default": "52.52" }, "lon": { "default": "13.41" }, "name": { "default": "Berlin" } },
  "data": {
    "weather": {
      "url": "https://api.open-meteo.com/v1/forecast?latitude={{params.lat}}&longitude={{params.lon}}&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto",
      "timeoutMs": 5000,
      "cacheSeconds": 600
    }
  },
  "lookups": { "wmo": { "0": "Clear sky", "1": "Mainly clear", "2": "Partly cloudy", "3": "Overcast", "*": "Unknown" } },
  "components": [
    { "type": "text", "style": "h1", "text": "{{weather.current.temperature_2m | round}}{{weather.current_units.temperature_2m}}" },
    { "type": "text", "text": "{{weather.current.weather_code | lookup:wmo}}" },
    { "type": "repeat",
      "items": { "day": "weather.daily.time", "max": "weather.daily.temperature_2m_max", "min": "weather.daily.temperature_2m_min" },
      "as": "d", "limit": 7,
      "template": [ { "type": "text", "text": "{{d.day | date:'EEE'}}  {{d.max | round}}° / {{d.min | round}}°" } ],
      "empty": [ { "type": "text", "text": "No forecast" } ] }
  ],
  "error": [ { "type": "text", "text": "Weather unavailable" },
             { "type": "button", "label": "Retry", "action": { "type": "reload" } } ]
}
```

### Model (`protocol/.../Screen.kt`, new `Binding.kt`)
New optional `ScreenDefinition` fields, all defaulted, so existing JSON stays valid:
- `params: Map<String, ParamSpec>`: `ParamSpec(default: String? = null)`; no default = required
  (missing → `ScreenParameterException` → 400, as today for DSL dynamic screens).
- `data: Map<String, DataSource>`: `DataSource(url, headers: Map<String,String> = {}, timeoutMs = 5000,
  cacheSeconds = 60, optional = false)`. GET only, JSON responses only.
- `lookups: Map<String, Map<String, String>>`: value tables for the `lookup` filter; `"*"` = fallback.
- `error: List<Component>`: shown instead of `components` when a non-optional source fails.
- New component `Component.Repeat(items: Items, as: String = "item", limit: Int? = null,
  template: List<Component>, empty: List<Component> = [])`.
  `items` is either a path string (`"shows"`, an array of anything) or an object of name → path to
  **parallel arrays**, zipped by index into objects (length = shortest array). This covers
  Open-Meteo (parallel arrays) and TVMaze (arrays of objects) without index expressions.
  `{{$index}}` is available inside the template (0-based).

### Template syntax (`{{ … }}` in string fields)
- Bindable fields: `text.text`, `image.url`, `image.alt`, `button.label`, `title`, action strings
  (navigate params, `open_url` url, track params). Non-string fields (spacer height) are not bindable.
  Strings without `{{` are unchanged; `{{` cannot be escaped yet (no use case).
- Expression: `path ( '|' filter (':' arg)* )*`. Path roots: `params`, a data-source name, a repeat
  `as` name, `$index`. Path steps: `.key`, `[n]`. Args: numbers or `'quoted'` strings.
- Filters (closed set, one file, each tested): `round[:digits]`, `date:'pattern'` (ISO date/date-time
  in → `DateTimeFormatter` pattern, locale `en` until Accept-Language lands), `lookup:table`,
  `default:'text'`, `upper`, `lower`. Unknown filter or lookup → load-time error.
- Missing path / null → empty string (then `default` applies); logged once per screen+path at WARN.
  Integral numbers print without `.0` (`21.0` → `21`), others as JSON.

### Secrets
`{{secret.NAME}}` reads the environment variable `COMPOSECAST_SECRET_NAME` and is allowed **only**
in `data.*.url` and `data.*.headers` (load-time error elsewhere), so secrets never enter a document.
Only prefixed variables are readable: an editor (admin panel) must not read `JAVA_OPTS` or other
server variables by putting `{{secret.X}}` into a URL. Missing variable → load-time error naming it.

### URL safety
Scheme + host of a source URL must be literal (no `{{` before the path) → load-time error otherwise;
substituted values are URL-encoded. `http`/`https` only, response ≤ 2 MB, redirects followed (like
`HttpImageLoader`).

### Execution (`sdk-server`)
- A screen with `data`, `params` or bindings is a **bound screen**: validated at load, rendered per
  request (like `dynamicScreen`). Other screens stay pre-rendered (no behavior change).
- Per request: resolve params (defaults) → fetch all sources **in parallel** (`DataLoader` fun
  interface, default `HttpDataLoader` on `java.net.http.HttpClient.sendAsync`; fake in tests) →
  bind = model-to-model transform (templates → concrete `ScreenDefinition`, repeats expanded) →
  `validate()` → existing `RemoteComposeRenderer` (**renderer unchanged**).
- Cache per resolved URL + headers: in-memory LRU (256), `cacheSeconds` TTL; failures cached 10 s
  (an API outage must not multiply requests). ETag still hashes the output → unchanged data = `304`.
- Errors: a failing non-optional source (timeout, non-2xx, invalid JSON, too large) → the screen's
  `error` components (bindable: `{{params.*}}` only), or, when none, HTTP **502**
  `{"error":"data source 'weather' failed: timeout after 5000 ms"}` → the app's existing error state
  with retry. A failing optional source binds as missing. Logged at WARN with source + URL, secret
  values replaced by `***`.
- `Routing.kt`: call `render` inside `withContext(Dispatchers.IO)` (it blocks on fetches; image
  fetching already blocks today).

### DSL
`screen("x") { param("lat", default = "52.52"); data("weather", url = "…", cacheSeconds = 600);
lookup("wmo", mapOf(…)); text("{{weather.current.temperature_2m | round}}");
repeat(items = mapOf("day" to "weather.daily.time"), as = "d") { text("{{d.day}}") }; error { … } }`
produces the same `ScreenDefinition` as JSON (existing `JsonScreensTest` pattern).

## Steps (files to touch)
1. `protocol/.../Binding.kt`: template parser (`Template.parse` → literal/expression parts),
   filter set, problems with JSON paths. `Screen.kt`: new fields, `Repeat`, `ParamSpec`,
   `DataSource`, recursive `problems()` (unknown root, filter, lookup, `secret` outside sources,
   non-literal host, `as` shadowing a source name). `ScreenJsonTest`, `BindingTest`.
2. `sdk-server/.../DataLoader.kt` (`DataLoader`, `HttpDataLoader`, cache), `Binder.kt`
   (model-to-model bind, filters on `JsonElement`), `ScreenRegistry.kt` (bound screens in the
   per-request path, `DataSourceException` → 502 or `error` components), `ScreenDsl.kt`;
   `composeCast(imageLoader, dataLoader)`. Tests: binder (paths, zip, limit, empty, filters, missing),
   parallel fetch, cache TTL, failure → error components / exception, secret masking.
3. `server-app`: `Routing.kt` (`Dispatchers.IO`), `Main.kt` (`StatusPages`: `DataSourceException` →
   502). `RoutingTest`: 502 body, `error` components, param default/required.
4. Demo (generic, not the weather app): screen `data` in `demoScreens()` +
   `examples/demo/screens/data.json` using Open-Meteo current temperature (keyless), linked from
   `main`; `compat-tests` screenshot with a fake `DataLoader` + fixture JSON (no network in CI),
   including a repeat over parallel arrays and the `error` state.
5. Emulator check against `docker compose up` (real Open-Meteo):
   [verify-on-emulator.md](../playbooks/verify-on-emulator.md).

## Acceptance criteria
- `examples/demo/screens/data.json` shows live Open-Meteo data in the app via Docker (manual check),
  and fixture data in the screenshot test; DSL and JSON definitions render byte-identically.
- `/screens/data?lat=…&lon=…` changes the data; a missing required param → 400.
- Source down → `error` components (or 502 + app error state with retry when none).
- Second request within `cacheSeconds` makes no HTTP call (counting fake loader).
- `{{secret.X}}` in a component → startup error naming file + JSON path; in a URL → resolved, and
  the value never appears in logs or `/debug`.
- Static screens unchanged (existing goldens untouched); `./scripts/verify.sh` green.

## Verification
`./scripts/verify.sh` (+ `--docker`); emulator against the container with real Open-Meteo.

## Docs to update
ADR 0008 (accepted with this design; adjust if implementation differs), `protocol/AGENTS.md`
(format), `sdk-server/AGENTS.md`, `server-app/AGENTS.md`, README (Defining screens → data sources;
Etap 4 boxes), `docs/history.md`.

## Out of scope
POST/GraphQL sources, chained sources, Accept-Language, binding non-string fields, client-side
refresh intervals (RC timers, T-020), shared source definitions across screens (add when two
screens duplicate one), the admin-panel editor for sources (T-007 uses this model).
