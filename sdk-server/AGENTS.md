# sdk-server — AGENTS.md

Kotlin DSL facade for building server-driven screens.

## Scope

This module only. Load `protocol/AGENTS.md` for type definitions.
Do NOT load `server-app`, `sdk-android`, or other modules unless cross-cutting.

## Key Files

| File | Purpose |
|---|---|
| `src/main/kotlin/io/composecast/server/sdk/ScreenDsl.kt` | `screen {}` DSL builder |
| `src/main/kotlin/io/composecast/server/sdk/ScreenRegistry.kt` | `composeCast {}` registry builder |

## Usage

```kotlin
val registry = composeCast {
    brand(name = "MyApp", primaryColor = "#6200EE", secondaryColor = "#03DAC6")
    screen("home") {
        title = "Home"
        text("Hello", style = "h1")
        button("Go", action = "navigate:detail")
    }
}
```

## Dependencies

- `:protocol` — ScreenModel, ComponentModel, BrandConfig

## Test

```bash
./gradlew :sdk-server:test
```
