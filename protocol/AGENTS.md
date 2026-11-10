# protocol — AGENTS.md

Shared types and constants used by server, admin and Android client, including the **screen
model** (`ScreenDefinition`), the source format of every screen — DSL, JSON files, admin panel
([ADR 0015](../docs/decisions/0015-screen-model-is-the-product.md)). The model is **not** a wire
format: apps receive Remote Compose documents rendered from it by `sdk-server`.

## Scope

This module only. **Do not** import `sdk-server`, `server-app`, or `sdk-android` from here.

## Key files

| File | Purpose |
|---|---|
| `Screen.kt` | `ScreenDefinition` (+ `params`, `data`, `lookups`, `error`), `Component` (Text, Image, Button, Spacer, Repeat), `TextStyle`, validation (`problems()`/`validate()`, incl. bindings), `ScreenJson` |
| `Binding.kt` | Data sources (T-006, [ADR 0008](../docs/decisions/0008-server-side-data-sources.md)): `ParamSpec`, `DataSource`, `RepeatItems` (path or zipped parallel arrays), `Template` parser for `{{path \| filter:arg}}`, `Filters` (names/arity), allowed roots |
| `Action.kt` | `Action` (Navigate, OpenUrl, Reload, Track, Custom) + `ActionCodec` (wire form) |
| `Actions.kt` | `Actions` (wire type names, used only by the codec), `Headers` (HTTP headers) |
| `BrandConfig.kt` | Brand colors + font, served as JSON by `GET /brand` |

## Actions wire format

Inside a Remote Compose document an action is the host-action name (a string, max 4000 chars):
`navigate:<enc screen>[?<enc k>=<enc v>&…]`, `open_url:<url raw>`, `reload:`,
`track:<enc event>[?params]`, `custom:<payload raw>`; `enc` = URL encoding (UTF-8). Params keep
insertion order. `ActionCodec.decode` returns `null` for unknown/malformed input (old apps ignore
newer actions); there is no "unknown" `Action`, so JSON definitions stay strict.
Details and test cases: `ActionCodecTest`.

## Screen JSON format

One screen per file, named `<id>.json`. Discriminator `"type"`; defaults (`title` = id,
`style` = `body`, `spacer.height` = 8, empty `params`) may be omitted and are not encoded.
Strict: unknown keys/types fail with the source and the JSON path.

```json
{ "id": "main", "title": "Home", "components": [
    { "type": "text", "text": "Welcome", "style": "h1" },
    { "type": "image", "url": "https://picsum.photos/400/200", "alt": "Hero" },
    { "type": "spacer", "height": 16 },
    { "type": "button", "label": "Open", "action": { "type": "navigate", "screen": "movie", "params": { "id": "42" } } }
] }
```
Actions: `navigate` (screen, params), `open_url` (url), `reload`, `track` (event, params),
`custom` (payload). Full examples: `examples/demo/screens/`; cases: `ScreenJsonTest`.

### Data sources and bindings (T-006)

A screen may declare `params` (`{"lat": {"default": "52.52"}}`; no default = required → 400),
`data` (name → `{url, headers, timeoutMs=5000, cacheSeconds=60, optional=false}`, GET + JSON only),
`lookups` (name → value table, `"*"` = fallback) and `error` (components shown when a non-optional
source fails; without them the server answers 502). String fields (`title`, `text.text`,
`image.url/alt`, `button.label`, action strings) may contain `{{path | filter:arg}}`:
- roots: `params`, a source name, a repeat `as` name, `$index`; steps `.key`, `[n]`;
- filters: `round[:digits]`, `date:'EEE'`, `lookup:table`, `default:'text'`, `upper`, `lower`;
- missing value → empty string; integral numbers print without `.0`;
- `{{secret.NAME}}` = env `COMPOSECAST_SECRET_NAME`, **only** in `data.*.url`/`headers`;
  scheme + host of a source URL must be literal; substituted values are URL-encoded.

`{"type": "repeat", "items": "shows" | {"day": "meteo.daily.time", "max": "…"}, "as": "d",
"limit": 7, "template": […], "empty": […]}` — an object of paths zips parallel arrays by index.
Everything is checked at load (unknown root/filter/lookup, misplaced secret) with the JSON path.
Full example: `examples/demo/screens/data.json`; cases: `BindingTest`, `DataBindingTest`.

## Rules

- **No RC types here** — `protocol` must stay pure Kotlin + kotlinx.serialization. No Remote
  Compose imports ever ([ADR 0002](../docs/decisions/0002-confine-restricted-remote-compose-api.md),
  rule R3 in `scripts/check-architecture.sh`).
- Only what server **and** app must agree on goes here (plus the screen model, ADR 0015); no
  speculative placeholders — unused `BrandTokens`, `Variables`, `Headers.BRAND_ETAG` were removed
  (2026-11-07). New shared constants (action types, header names) go here, not in
  `sdk-server` or `sdk-android`.
- Adding a field to `BrandConfig` is a protocol change — update both the server
  DSL and the Android client.

## Dependencies

None (pure Kotlin + kotlinx.serialization).

## Test

```bash
./gradlew :protocol:test   # ActionCodecTest, ScreenJsonTest
```
