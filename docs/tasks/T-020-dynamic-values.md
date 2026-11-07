# T-020 — Dynamic values: live clock, formatted numbers, light/dark colors, conditions

Status: queued (after T-017) · Roadmap: Etap 7 / Etap 10 · Depends on: T-012

## Goal

Values computed on the device: current time/date text, countdowns, formatted numbers, colors that
follow light/dark mode, and parts of the UI shown only under device conditions (width, API level).

## Why

RC alpha23 has time sources (`hour()`, `minutes()`, `seconds()`, `dayOfMonth()`, `month()`,
`utcOffset()`, `timeAttribute`), `createTextFromFloat`/`format`, `textMerge`, `textLookup`,
`windowWidth/Height()`, `apiLevel()`, `ifTrue`/`conditionalOperations`/`skip`,
`remoteThemedColor(light, dark)` (`docs/remote-compose.md` → Procedural catalog). A document cached
for hours still shows the right time and theme.

## Design

- Text templates with device placeholders: `"text": "{{device.time:HH:mm}}"` — **coordinate with
  T-006** (`{{...}}` is its binding syntax; device values must not collide with server bindings,
  e.g. a `device.` namespace resolved at play time, server ones at render time).
- `countdown { to: ISO-8601 }` (server converts to epoch; device computes remaining).
- Colors: every color field accepts `{ "light": "#…", "dark": "#…" }` → `remoteThemedColor`;
  brand tokens light/dark (Etap 7 box) build on this.
- `modifier.showIf: { windowWidthAtLeast: 600 }` → `conditionalOperations` (verify it works inside
  layout, not only canvas).

## Steps

Verify each primitive on the emulator first (clock updates every minute without re-fetch;
themed color switches with `adb shell cmd uimode night yes`) → model → renderer → tests → demo
`dynamic` (+ JSON) → screenshots in light and dark → emulator.

## Acceptance criteria

- Clock text updates on the device without a request; dark mode switches colors live.
- `showIf` hides a section on phone width and shows it on tablet width.
- `./scripts/verify.sh` green; light + dark goldens reviewed.

## Docs to update

`docs/remote-compose.md`, ADR if the placeholder syntax is shared with T-006, `protocol/AGENTS.md`,
`sdk-server/AGENTS.md`, README Etap 7/10 boxes, `docs/history.md`.

## Out of scope

Server data bindings (T-006), sensors (T-022).
