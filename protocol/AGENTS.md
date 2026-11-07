# protocol — AGENTS.md

Shared types and constants used by both server and Android client.
Screens themselves are **not** described here: they travel as Remote Compose binary documents
built by `sdk-server`. (The old JSON `ScreenModel`/`ComponentModel` were removed during the cleanup,
commit `ab79b5f`. A new *authoring* model that can be stored as JSON is planned in `sdk-server` —
docs/tasks/T-002 — it is not a wire format; screens still travel as RC documents.)

## Scope

This module only. **Do not** import `sdk-server`, `server-app`, or `sdk-android` from here.

## Key files

| File | Purpose |
|---|---|
| `Actions.kt` | `Actions` object (NAVIGATE, OPEN_URL, TRACK, CUSTOM); `Headers` (player version, brand ETag, screen title); `Variables` (user_name, cart_count) |
| `BrandConfig.kt` | `BrandConfig` — name, primaryColor, secondaryColor, backgroundColor, fontFamily |
| `BrandTokens.kt` | `BrandTokens` — light/dark `ColorScheme`, `Shapes`, `Typography` |

## Rules

- **No RC types here** — `protocol` must stay pure Kotlin + kotlinx.serialization. No Remote
  Compose imports ever ([ADR 0002](../docs/decisions/0002-confine-restricted-remote-compose-api.md),
  rule R3 in `scripts/check-architecture.sh`).
- All new shared constants (action types, header names, variable names) go here, not in
  `sdk-server` or `sdk-android`.
- Adding a field to `BrandTokens`/`BrandConfig` is a protocol change — update both the server
  DSL and the Android client.

## Dependencies

None (pure Kotlin + kotlinx.serialization).

## Test

```bash
./gradlew :protocol:build
```
