# 0021 — Client–server version negotiation (compatibility policy)

- Status: Accepted
- Date: 2026-11-16

## Context

Installed Android apps carry a fixed Remote Compose player (`CoreDocument.DOCUMENT_API_LEVEL` and
profile bitmask), while the server may be upgraded independently. If the server emits operations
newer than the player understands, the app fails to decode or mis-renders screens.

Verified on RC `1.0.0-alpha23`: `CoreDocument.DOCUMENT_API_LEVEL = 8`, `RcProfiles.PROFILE_ANDROIDX = 512`,
and `Header.DOC_DENSITY_BEHAVIOR` requires API level 8+ (`RemoteComposeWriter.filterAndValidateTags`).

## Decision

1. **Client headers**: `ComposeCastClient` sends `X-Composecast-Rc-Api-Level` and
   `X-Composecast-Rc-Profiles` on every `GET /screens/{id}` request. Missing headers (older
   clients) fall back to the server's default level and are logged.
2. **Server rendering**: `ScreenRegistry.render` / `RemoteComposeRenderer.render` renders for
   `min(clientLevel, serverMaxLevel)` and caches static screens per `(id, apiLevel, profiles)`.
   Because `RemoteScreen.etag` hashes the rendered bytes, `If-None-Match` stays version-safe.
3. **Minimum / unsupported version**: If `clientLevel < MIN_API_LEVEL` (currently 8 because of
   `DOC_DENSITY_BEHAVIOR`) or the RC writer rejects an operation at the client's level, the server
   throws `UnsupportedClientVersionException` and responds `426 Upgrade Required` with JSON
   `{"error": "...", "minApiLevel": N}`. `sdk-android` surfaces this as
   `ComposeCastUpgradeRequiredException` and `app-shell` displays `"Please update the app to continue."`.

## Consequences

- Server upgrades never silently break installed apps: either the server targets the client's RC
  API level or cleanly asks the user to update.
- When `remote-compose` is upgraded, `MAX_API_LEVEL` advances automatically via
  `CoreDocument.DOCUMENT_API_LEVEL`, while `MIN_API_LEVEL` remains explicit.
