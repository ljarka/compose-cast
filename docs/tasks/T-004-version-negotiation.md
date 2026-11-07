# T-004 — Version negotiation

Status: ready (after T-003) · Roadmap: Etap 2 · Depends on: T-003

## Goal
Clients report which Remote Compose documents they can play; the server renders for that, or
answers "update required" — so a server change never breaks an installed app (Vision principle 2).

## Facts (docs/remote-compose.md)
`CoreDocument.DOCUMENT_API_LEVEL = 8` (alpha20, alpha23); the writer accepts levels 6–8 for our
documents and validates operations per level at creation time; profile `PROFILE_ANDROIDX` (512).

## Design
- Headers (protocol): `X-Composecast-Rc-Api-Level` (player's `CoreDocument.DOCUMENT_API_LEVEL`),
  `X-Composecast-Rc-Profiles` (bit mask). SDK sends them on every document request (read the
  constants from `remote-core` inside `sdk-android`, rule R1).
- Server: render with `Profile(apiLevel = min(client, server), …)`. If the screen cannot be written
  at the client's level (writer throws), or the level is below the supported minimum → `426 Upgrade
  Required` with JSON `{ "error": "...", "minApiLevel": N }`; the app shows an "update the app" state.
- Missing headers (old clients) → server default level, logged.
- Include the level in the ETag/cache key (T-003).

## Acceptance criteria
- Tests render the demo at levels 6, 7, 8 and decode each; level 5 → 426.
- App sends the headers (verify with server log / test).

## Docs to update
`docs/remote-compose.md` (versions section), new ADR "Compatibility policy", README (compatibility),
`docs/history.md`.
