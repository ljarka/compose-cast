# protocol — AGENTS.md

Shared types and constants used by both server and Android client.
Screens themselves are **not** described here: they travel as Remote Compose binary documents
built by `sdk-server`. (The old JSON `ScreenModel`/`ComponentModel` were removed during the cleanup,
commit `ab79b5f`. A new *authoring* model that can be stored as JSON is planned in `sdk-server` —
docs/tasks/T-002 — it is not a wire format; screens still travel as RC documents.)

## Scope

This module only. No dependencies on other compose-cast modules.

## Key Files

| File | Type |
|---|---|
| `Actions.kt` | `Actions` (host action types), `Headers` (HTTP headers), `Variables` (unused placeholders) |
| `BrandConfig.kt` | Brand colors + font, served as JSON by `GET /brand` |
| `BrandTokens.kt` | Full Material3 token set (light/dark/shapes/typography); not used yet |

## Rules

- JSON-carried types are `@Serializable` (kotlinx.serialization); exposed as `api` dependency
- No business logic here — only data types and constants
- No Remote Compose dependency here

## Test

```bash
./gradlew :protocol:test
```
