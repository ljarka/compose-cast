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
| `Layout.kt` | `Modifier` (box model, ADR 0020, incl. `scroll`, `semantics`), `Padding` (number or sides), `Dimension` (dp / `fill` / `wrap`), `Border`, `Shape`, `Alignment`, `Arrangement`, `ScrollDirection`, `Semantics`, `SemanticsRole` + their JSON serializers |
| `Screen.kt` | `ScreenDefinition` (+ `params`, `data`, `lookups`, `error`; `problems()`/`contentProblems()`/`warnings()`/`validate()`), `ScreenJson` |
| `ScreenValidation.kt` | All screen validation (structure, modifiers, colors, actions, bindings, source URLs), called by the `ScreenDefinition` members |
| `Component.kt` | `Component` (Text, Image, Icon, Divider, Card, Button, Spacer, Repeat, Expandable, Column, Row, Box, FitBox, Flow, Grid), `strings()`/`childLists()`/`hasBindings()`, `Action.strings()` |
| `ComponentOptions.kt` | `TextStyle`, `FontWeight`, `TextAlign`, `TextOverflow`, `ContentScale`, `ButtonVariant` |
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

One screen per file, `<id>.json`; discriminator `"type"`; defaults omitted; strict (unknown keys
fail with the source and the JSON path). Every component, modifier, binding and filter:
**[docs/screen-json-format.md](../docs/screen-json-format.md)** — update it with every model change.
Full examples: `examples/demo/screens/`; cases: `ScreenJsonTest`, `LayoutTest`, `BindingTest`.

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

## Publishing (T-024)

Published as `io.composecast:protocol:<version>` (`maven-publish`; repository setup in the root
`build.gradle.kts`: dry run into `build/maven-dry-run`, Reposilite in the release workflow). Its
public API is used by published apps: a breaking change needs a new compose-cast version.
