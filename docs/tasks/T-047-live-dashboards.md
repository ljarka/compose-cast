# T-047 — Live screens + dashboard components (gauge, sparkline, KPI)

Status: queued · Roadmap: G (live dashboards) · Depends on: ADR 0029 · Module: protocol, sdk-server, server-app, sdk-android, app-shell, compat-tests

## Goal
A screen marked `live` keeps itself current on the phone with no refresh button: the server pushes
updates over a WebSocket (up to several per second), and the screen shows **dashboard components**
drawn with RC `Canvas` that animate between values. Screens without `live` behave exactly as today.

## Why
Owner, 2026-11-24: beautiful live dashboards on Remote Compose; static screens stay as they are;
live + canvas is for advanced screens. The PoC proved the mechanism (ADR 0029,
`docs/remote-compose.md` → Live updates): data-update documents, 10 updates/s, ≈ 4 ms per apply.

## Design (ADR 0029)
- **Model** (`protocol`): `ScreenDefinition.live: Live? = null` with `everyMs` (min 100, default
  1000). Components: `gauge { value 0..1 | binding, label, color, trackColor }`,
  `sparkline { values: [..] | binding, color, height }`, `kpi { value, label, format }` (big
  number + label; text full width). JSON + DSL + `problems()` validation; palette entries in the
  admin visual editor (generated forms, ADR 0028).
- **Renderer** (`sdk-server/render`): new `Dashboard.kt`. Values and arrays declared in
  `Global { }` (behavior 18), arcs/lines via `componentWidth()/componentHeight()`,
  `anim()` on float expressions only, never on array elements (behavior 19). `render(...,
  dataUpdate = true)` adds `HTag(Header.DOC_DATA_UPDATE, 1)`.
- **Server** (`server-app`): `ktor-server-websockets`; `webSocket("/screens/{id}/live")` with the
  same params/version headers as GET. Loop: render → if structure changed send full doc, else if
  bytes changed send data update → `delay(everyMs)`. Structure check: render twice is not needed —
  compare `RemoteComposeRenderer.describe`-style structure (ids + op types, values masked) or a
  fingerprint the renderer returns. 404/400 for non-live screens. Share one loop per
  `(screen, params)` between connections (fan-out) — can be a follow-up if noted.
- **Client** (`sdk-android`): `ComposeCastClient.liveScreen(id, params): Flow<LiveFrame>`
  (Ktor client + OkHttp engine + `WebSockets`); `ComposeCastScreen` applies frames
  (`setDocument` / `updateDocument`), reconnects with backoff, stops in background. RC types stay
  inside `sdk-android` (ADR 0002).
- **Shell** (`app-shell`): a screen whose response says it is live (header
  `X-Composecast-Live: everyMs` on the GET) switches to the live flow; no UI change otherwise.
- **Demo**: `dashboard` screen (DSL + JSON) with a fake metrics data source (fixture loader in
  tests, a tiny `/demo/metrics` JSON endpoint on the demo server so it moves without internet).

## Steps (commit each)
1. Model + JSON + validation + renderer for `gauge`/`sparkline`/`kpi` (static) → golden
   `dashboard.png`; check two widths.
2. `DOC_DATA_UPDATE` render + server WebSocket route + `ServerLiveTest` (first frame full, next
   frames update flag + same ids, unchanged data sends nothing, non-live → 404).
3. `sdk-android` live flow + `ComposeCastScreen` + shell switch; emulator: dashboard moves,
   `adb logcat -s ComposeCast` shows updates; background → socket closed (server log).
4. Admin: `live` field in screen settings, components in the palette.
5. Docs.

## Acceptance criteria
- Static screens: byte-identical documents and unchanged goldens.
- On the emulator the demo dashboard updates every second for 1 min without user action and
  without dropped frames; gauge animates between values; going to background closes the socket.
- `./scripts/verify.sh` green; goldens reviewed.

## Docs to update
`docs/screen-json-format.md` (live + 3 components), `docs/remote-compose.md` (anything new),
`protocol`/`sdk-server`/`server-app`/`sdk-android`/`app-shell` `AGENTS.md`, playbook
`verify-on-emulator.md` (live check), feature checklist, history, README screenshot.

## Out of scope
Live edit preview (T-032 — reuses this channel), bar/area charts and gradients (T-018), upstream
push sources (WebSocket/SSE from the external API), app-side values (`setUserLocalFloat`, T-020).
