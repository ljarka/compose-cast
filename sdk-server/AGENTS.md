# sdk-server — AGENTS.md

Kotlin DSL facade that renders server-driven screens to **Remote Compose binary documents**.

## Scope

This module only. Load `protocol/AGENTS.md` for shared types.
Do NOT load `server-app`, `sdk-android`, or other modules unless cross-cutting.

## Key Files

| File | Purpose |
|---|---|
| `ScreenDsl.kt` | `ScreenBuilder` (`text`, `image`, `button`, `spacer`, `expandable`, `repeat`, `param`, `data`, `lookup`, `error`) → `ScreenDefinition` |
| `DataLoader.kt` | `DataLoader` fun interface (`DataRequest` → body), `HttpDataLoader` (http/https, ≤ 2 MB, redirects), `DataSourceException` (→ 502) |
| `Binder.kt` | T-006 execution: `DataFetcher` (parallel fetch, secrets from env), `DataCache` (LRU 256 per URL + headers, TTL `cacheSeconds`, failures 10 s), `BoundScreen` (params → fetch → model-to-model bind → `validate()`), filter implementations, secret masking in logs |
| `ScreenRegistry.kt` | `composeCast {}` builder (`screen {}`, `dynamicScreen {}`, `screen(definition)`, `screensFrom(dir)`, `brand`), `ScreenRegistry` (`render(id, RenderContext)`), `RenderContext`, `ScreenParameterException`, `RemoteScreen` (definition, bytes, `etag`) |
| `ImageCache.kt` | Thread-safe LRU (256) of fetched images; failures cached 60 s only |
| `RemoteComposeRenderer.kt` | **Only** file using the Remote Compose creation API; `ScreenDefinition` → RC bytes |

The screen model itself (`ScreenDefinition`, `Component`, `TextStyle`, `ScreenJson`, validation)
is in `protocol/.../Screen.kt` ([ADR 0015](../docs/decisions/0015-screen-model-is-the-product.md)).
| `ImageLoader.kt` | `ImageLoader` fun interface + `HttpImageLoader` (http/https only, max 5 MB); inject a fake one in tests |

## Usage

```kotlin
val registry = composeCast {
    brand(name = "MyApp", primaryColor = "#6200EE", secondaryColor = "#03DAC6")
    screen("home") {
        title = "Home"
        text("Hello", style = "h1")          // h1 | h2 | h3 | body | caption
        image("https://example.com/a.png", alt = "Hero")
        expandable("More info", expanded = false) { text("Toggled locally on the device") }
        button("Go", Action.Navigate("detail", mapOf("id" to "42"))) // RC hostAction("navigate:detail?id=42")
        button("Docs", action = "open_url:https://example.com")     // wire form, validated at build
    }
}

// Same model from JSON files, one <id>.json per screen (format: protocol/AGENTS.md):
composeCast { brand(myBrand); screensFrom(Path.of("data/screens")) }

// Rendered per request from query params (/screens/movie?id=42); ctx.param() missing → 400:
composeCast { dynamicScreen("movie") { ctx -> text("Movie ${ctx.param("id")}") } }

// Bound screen (T-006): params + HTTP data sources + {{bindings}}, rendered per request:
composeCast(dataLoader = HttpDataLoader) {   // inject a fake DataLoader in tests
    screen("weather") {
        param("lat", default = "52.52")
        data("meteo", url = "https://api.open-meteo.com/v1/forecast?latitude={{params.lat}}&current=temperature_2m", cacheSeconds = 600)
        text("{{meteo.current.temperature_2m | round}}°", style = "h1")
        repeat(zip = mapOf("day" to "meteo.daily.time"), alias = "d") {  // or repeat("path.to.array") text("{{d.day | date:'EEE'}}") }
        error { text("Weather unavailable"); button("Retry", Action.Reload) }
    }
}

registry.render("movie", RenderContext(mapOf("id" to "42"))) // static: cached doc; bound/dynamic: fresh render
registry.get("home")?.document  // ByteArray, pre-rendered static screens only
registry.get("home")?.definition // ScreenDefinition; ScreenJson.encode(it) exports DSL screens to JSON
registry.get("home")?.describe() // decoded operation tree, for debugging
```

Screens are rendered once in `composeCast {}` (after the brand is known). Invalid input fails at
build time: DSL parsing (unknown text style or wire-form action) in `ScreenBuilder`, content
(`ScreenDefinition.validate()`: negative spacer, action that cannot reach the app or > 4000 chars,
blank id) when the screen is added, duplicate id / bad color in the registry. JSON errors name the
file and the JSON path; a file must be named `<id>.json`.

## Decisions

- RC creation API is confined to `RemoteComposeRenderer` ([ADR 0002](../docs/decisions/0002-confine-restricted-remote-compose-api.md)).
- Buttons are a clickable `Box` with brand `primaryColor` background; click = `hostAction(action)`.
- `Component.Expandable` (T-008) renders a `remoteInteger(0)` state and a `Box`-wrapped
  `StateLayout` with two branches (initial state at index 0, toggled state at index 1) whose headers
  call `setValue(state, nextState)` — expanding/collapsing happens entirely on the device.
- Images are fetched at render time and embedded, named `image:<url>`; failed fetch → alt text
  ([ADR 0003](../docs/decisions/0003-embed-images-server-side.md)).
- Units: DP density header; sizes in dp; fonts via `sp()` expression; image height expression in
  dp wrapped in `written(...)` ([ADR 0004](../docs/decisions/0004-units-dp-and-sp.md) — re-check its
  table on every RC upgrade).
- Static screens (DSL `screen`, JSON) are rendered once in `composeCast {}` (validation = fail
  fast) and ignore params; `dynamicScreen` is built, validated and rendered on every `render`.
  Params are query parameters, ids stay single path segments (they name `<id>.json` files) — T-003.
- Data sources (T-006, [ADR 0008](../docs/decisions/0008-server-side-data-sources.md)): a screen with
  `params`, `data` or `{{ }}` is **bound** — validated (incl. secrets present in env, date patterns)
  at build, then per request fetched in parallel, bound model-to-model and rendered by the
  unchanged renderer. `render()` blocks on fetches: call it off request threads. A failing
  non-optional source → `error` components, else `DataSourceException`. Secret values are replaced
  by `***` in logs; they never enter a document (secrets only allowed in source URLs/headers).
- `RemoteScreen.etag` = SHA-256 of the bytes (first 16 bytes, hex): covers everything rendering
  read, so no cache key needs maintaining when `RenderContext` grows (Etap 12 targeting).
- Rendering is thread-safe: one `RemoteComposeRenderer` per registry; `createRcBuffer` per call;
  shared state only in `ImageCache` and `DataCache` (verified by a 50-thread test).

## Dependencies

- `:protocol` (api) — screen model, `Action`, `BrandConfig`
- `remote-creation-core`, `remote-creation-jvm`, `remote-core` (implementation)

## Test

```bash
./gradlew :sdk-server:test
```
