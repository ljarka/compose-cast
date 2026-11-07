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
| `Action.kt` | `Action` (Navigate, OpenUrl, Reload, Track, Custom) + `ActionCodec` (wire form) |
| `Actions.kt` | `Actions` (wire type names, used only by the codec), `Headers` (HTTP headers), `Variables` (unused placeholders) |
| `BrandConfig.kt` | Brand colors + font, served as JSON by `GET /brand` |
| `BrandTokens.kt` | Full Material3 token set (light/dark/shapes/typography); not used yet |

## Actions wire format

Inside a Remote Compose document an action is the host-action name (a string, max 4000 chars):
`navigate:<enc screen>[?<enc k>=<enc v>&…]`, `open_url:<url raw>`, `reload:`,
`track:<enc event>[?params]`, `custom:<payload raw>`; `enc` = URL encoding (UTF-8). Params keep
insertion order. `ActionCodec.decode` returns `null` for unknown/malformed input (old apps ignore
newer actions); there is no "unknown" `Action`, so JSON definitions stay strict.
Details and test cases: `ActionCodecTest`.

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
./gradlew :protocol:test   # ActionCodecTest
```
