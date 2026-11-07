# protocol — AGENTS.md

Shared serializable types used by both server and Android client.

## Scope

This module only. No dependencies on other compose-cast modules.

## Key Files

| File | Type |
|---|---|
| `ScreenModel.kt` | Root screen descriptor |
| `ComponentModel.kt` | Sealed class: Text, Image, Button, Spacer |
| `BrandConfig.kt` | Simple brand colors + font |
| `BrandTokens.kt` | Full Material3 token set (light/dark/shapes/typography) |
| `Actions.kt` | Action and header name constants |

## Rules

- All types must be `@Serializable` (kotlinx.serialization)
- `ComponentModel` subtypes must have `@SerialName` discriminators
- No business logic here — only data types and constants

## Test

```bash
./gradlew :protocol:test
```
