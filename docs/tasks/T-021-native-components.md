# T-021 — Native components through RC `Custom`: text input, search, maps, video

Status: needs design detail (spike first) · Roadmap: Etap 10 · Depends on: T-012, T-017

## Goal

Components RC cannot draw — **text input/search**, map, video, web view — hosted as native Android
views inside an RC document, configured from the server.

## Why

RC alpha23 has **no text-input operation** (verified: nothing in `Rc.java`/`RcTypes.kt` besides
keyboard haptics). It does have `RcScope.Custom(config, modifier, properties, content)` (layout
manager `Custom.java`: "host and configure platform-specific native custom components") and the
View player accepts `RemoteComposePlayer.setCustomSupport(AndroidCustomContext)`
(`remote-player-view` alpha23). Rule 1 forbids a custom renderer, but hosting a native view at a
position RC laid out is RC's own extension point.

## Spike questions (answer from sources, record in `docs/remote-compose.md`)

1. `AndroidCustomContext` (in `remote-player-core`): how is a native `View` created, measured,
   positioned, and destroyed? How do `CustomProperty` values and touch reach it?
2. Can the native view write back into a document variable (string/int) so a later `hostAction`
   carries the typed text (`HostNamedActionOperation.valueId`)?
3. Does it work in Robolectric screenshot tests (placeholder?) and with `-gpu host` emulator?
4. Same idea on iOS (ADR 0007 outcome) — note only.

## Design (after the spike)

- `textField { name, placeholder, keyboard: text|number|email, submitAction }`,
  `search { … }`, later `map`, `video`, `webView` — each one Android factory in `sdk-android`
  behind a registry (`ComposeCastNativeComponents`), unknown type → RC fallback child (`content`
  of `Custom`).
- Submitted values travel as action params (`navigate:search?q=…`).
- New ADR: native components via RC `Custom` (relation to ADR 0001).

## Acceptance criteria

- Spike: answers 1–4 in `docs/remote-compose.md`; go/no-go ADR.
- If go: a search screen where typing + submit navigates to a dynamic results screen
  (`/screens/search?q=…`), verified on the emulator; screenshot with fallback.

## Docs to update

ADR, `docs/remote-compose.md`, `sdk-android/AGENTS.md`, `protocol/AGENTS.md`, README Etap 10 box,
`docs/history.md`.

## Out of scope

Arbitrary app-provided native widgets (later: plugin API).
