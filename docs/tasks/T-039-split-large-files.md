# T-039 — Split large files and long AGENTS.md (small context per change)

Status: done · Roadmap: chores · Depends on: — · Module: sdk-server, protocol, server-app

## Goal
No source file an agent must edit is longer than ~400 lines, and no `AGENTS.md` is longer than
150 lines (rule R9). Today `RemoteComposeRenderer.kt` is ~800 lines, `Screen.kt` ~730, `Screens.kt`
~470, `Binder.kt` ~400, and `protocol/AGENTS.md` is 133 lines.

## Design
- `sdk-server`: split the renderer into a package `io.composecast.server.sdk.render` (one file per
  component group: text, image, containers, collections, modifiers, semantics). Update rule R1
  (ADR 0002) to allow that package instead of the single file, in the same change, and update ADR
  0002's "How to verify".
- `server-app`: split the demo `Screens.kt` into one file per demo screen.
- `protocol/AGENTS.md`: move the JSON format reference to `docs/screen-json-format.md` and keep a
  short summary plus the link.
- Pure refactor: byte-identical documents (`JsonScreensTest`, screenshot tests unchanged).

## Acceptance criteria
- `./scripts/verify.sh` is green with **no** screenshot re-recording; R1 is updated; files are under
  ~400 lines.

## Result (2026-11-18)
- `sdk-server/.../render/`: `RemoteComposeRenderer.kt` 150, `Modifiers.kt` 160, `Nodes.kt` 105,
  `Containers.kt` 115, `Images.kt` 85, `Interactive.kt` 140, `Mappings.kt` 105, `Style.kt` 25 lines.
- `protocol`: `Screen.kt` 85, `ScreenValidation.kt` 305, `Component.kt` 220, `ComponentOptions.kt`
  140; public API unchanged (validation stays `ScreenDefinition` members, delegating).
- `server-app`: `Screens.kt` 75 + `demo/Demo<Id>Screen.kt` (≤ 95 each) + `DemoShared.kt`.
- `protocol/AGENTS.md` 133 → 68 lines; JSON reference in `docs/screen-json-format.md`.
- Byte-identity checked beyond the tests: SHA-256 of every demo document (DSL and JSON, API level
  max and 8) before and after, all 34 equal. `Binder.kt` (405) left as is (at the limit, not
  touched by this task's design).

## Docs to update
`sdk-server/AGENTS.md` (file list), `protocol/AGENTS.md`, ADR 0002, history.
