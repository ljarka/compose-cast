# sdk-server — AGENTS.md

Kotlin DSL facade that renders server-driven screens to **Remote Compose binary documents**.

## Scope

This module only. Load `protocol/AGENTS.md` for shared types.
Do NOT load `server-app`, `sdk-android`, or other modules unless cross-cutting.

## Key Files

| File | Purpose |
|---|---|
| `ScreenDsl.kt` | `ScreenBuilder` (`text`, `image`, `button`, `spacer`), `TextStyle`, internal `ScreenNode` |
| `ScreenRegistry.kt` | `composeCast {}` builder, `ScreenRegistry`, `RemoteScreen` (id, title, document bytes) |
| `RemoteComposeRenderer.kt` | **Only** file using the Remote Compose creation API; `ScreenNode` → RC bytes |
| `ImageLoader.kt` | `ImageLoader` fun interface + `HttpImageLoader`; inject a fake one in tests |

## Usage

```kotlin
val registry = composeCast {
    brand(name = "MyApp", primaryColor = "#6200EE", secondaryColor = "#03DAC6")
    screen("home") {
        title = "Home"
        text("Hello", style = "h1")          // h1 | h2 | h3 | body | caption
        image("https://example.com/a.png", alt = "Hero")
        button("Go", action = "navigate:detail") // emitted as RC hostAction("navigate:detail")
    }
}
registry.get("home")?.document  // ByteArray, Remote Compose document
registry.get("home")?.describe() // decoded operation tree, for debugging
```

Screens are rendered once in `composeCast {}` (after the brand is known). Invalid input
(unknown text style, unknown action type, duplicate id, bad color) fails at build time.

## Decisions

- remote-compose `1.0.0-alpha20`: the whole creation API is `@RestrictTo(LIBRARY_GROUP)` and
  unstable, so it is confined to `RemoteComposeRenderer` and not exposed publicly.
- Buttons are a clickable `Box` with brand `primaryColor` background; click = `hostAction(action)`.
- Images are fetched at render time (`ImageLoader`, default `HttpImageLoader`) and **embedded**
  in the document (original bytes, real size), named `image:<url>`. URL images do not work in the
  alpha20 player: disabled by default, size-checked against a 1x1 declared by the DSL, and fetched
  synchronously on the main thread. A failed fetch renders the alt text instead (logged warning).
- Units: header `DOC_DENSITY_BEHAVIOR = DP` makes the player scale padding/border/clip radius only.
  Font sizes (`FONT_SIZE * sp / 14`, honors font scale) and heights (`density * dp`) are emitted as
  device-side expressions. alpha20 bug: `height(RcFloat)` needs the expression written first
  (`written(...)`), otherwise it becomes `Height(EXACT, 0.0)` — covered by a test.

## Dependencies

- `:protocol` (api) — BrandConfig, Actions
- `remote-creation-core`, `remote-creation-jvm`, `remote-core` (implementation)

## Test

```bash
./gradlew :sdk-server:test
```
