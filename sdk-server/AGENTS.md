# sdk-server — AGENTS.md

Kotlin DSL facade that renders server-driven screens to **Remote Compose binary documents**.

## Scope

This module only. Load `protocol/AGENTS.md` for shared types.
Do NOT load `server-app`, `sdk-android`, or other modules unless cross-cutting.

## Key Files

| File | Purpose |
|---|---|
| `ScreenDsl.kt` | `ScreenBuilder` (`text`, `image`, `button`, `spacer`) → `ScreenDefinition` |
| `ScreenRegistry.kt` | `composeCast {}` builder (`screen {}`, `screen(definition)`, `screensFrom(dir)`, `brand`), `ScreenRegistry`, `RemoteScreen` (definition, document bytes) |
| `RemoteComposeRenderer.kt` | **Only** file using the Remote Compose creation API; `ScreenDefinition` → RC bytes |

The screen model itself (`ScreenDefinition`, `Component`, `TextStyle`, `ScreenJson`, validation)
is in `protocol/.../Screen.kt` ([ADR 0015](../docs/decisions/0015-screen-model-is-the-product.md)).
| `ImageLoader.kt` | `ImageLoader` fun interface + `HttpImageLoader`; inject a fake one in tests |

## Usage

```kotlin
val registry = composeCast {
    brand(name = "MyApp", primaryColor = "#6200EE", secondaryColor = "#03DAC6")
    screen("home") {
        title = "Home"
        text("Hello", style = "h1")          // h1 | h2 | h3 | body | caption
        image("https://example.com/a.png", alt = "Hero")
        button("Go", Action.Navigate("detail", mapOf("id" to "42"))) // RC hostAction("navigate:detail?id=42")
        button("Docs", action = "open_url:https://example.com")     // wire form, validated at build
    }
}

// Same model from JSON files, one <id>.json per screen (format: protocol/AGENTS.md):
composeCast { brand(myBrand); screensFrom(Path.of("data/screens")) }

registry.get("home")?.document  // ByteArray, Remote Compose document
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
- Images are fetched at render time and embedded, named `image:<url>`; failed fetch → alt text
  ([ADR 0003](../docs/decisions/0003-embed-images-server-side.md)).
- Units: DP density header; sizes in dp; fonts via `sp()` expression; image height expression in
  dp wrapped in `written(...)` ([ADR 0004](../docs/decisions/0004-units-dp-and-sp.md) — re-check its
  table on every RC upgrade).
- Screens are rendered once in `composeCast {}` (per-request rendering is planned, roadmap Etap 2).

## Dependencies

- `:protocol` (api) — screen model, `Action`, `BrandConfig`
- `remote-creation-core`, `remote-creation-jvm`, `remote-core` (implementation)

## Test

```bash
./gradlew :sdk-server:test
```
