# 0029 — Live screens: WebSocket + RC data-update documents (opt-in per screen)

- Status: Accepted
- Date: 2026-11-24

## Context
The owner wants **beautiful live dashboards** (gauges, charts, KPIs that keep moving) and a **live
preview** of edits on a phone (T-032), without a refresh button: an open connection that keeps the
screen current. Ordinary screens must keep working exactly as before (owner, 2026-11-24: "still
allow static sites like before; live with canvas is for much more advanced screens").

Verified in RC 1.0.0-alpha23 sources and in a PoC on the emulator (2026-11-24, details in
[remote-compose.md → Live updates](../remote-compose.md#live-updates-verified-alpha23-2026-11-24-poc-on-the-emulator)):
- A document with header `DOC_DATA_UPDATE = 1` is applied by the player with `applyUpdate`: texts,
  numbers, bitmaps and **float arrays** are replaced by id; layout, scroll and state stay.
- Rendering the same screen again with new data gives the same ids → the server needs no diffing:
  a data update is the normal render plus the header flag.
- 10 updates/s over a WebSocket: ≈ 4 KB each, ≈ 4 ms to apply, no dropped UI frames; `anim()`
  expressions animate between values on the device.
- Gotchas: named variables/arrays must be in `Global { }`; `anim()` on array elements freezes;
  updated texts need a fixed/full width (remote-compose.md behaviors 18–20).

## Decision
1. **Opt-in per screen.** A screen gets `live` (e.g. `"live": { "everyMs": 1000 }`); screens
   without it are served exactly as today (`GET /screens/{id}` + ETag, no connection).
2. **One WebSocket per open live screen**: `GET /screens/{id}/live?params` upgraded to a WebSocket.
   The server sends binary frames:
   - the **full document** first (and whenever the structure changes, detected by comparing the
     document's structure fingerprint, e.g. the bytes with data values masked or the id layout);
   - otherwise a **data-update document** (same render + `DOC_DATA_UPDATE`).
   The server re-renders on a timer (`everyMs`, data sources re-fetched respecting their
   `cacheSeconds`) and, for drafts, when the draft changes — the same channel serves **T-032 live
   preview** (the trigger is an edit instead of a timer).
3. **Frames skipped when nothing changed** (bytes equal to the previous frame) — a quiet dashboard
   costs nothing on the wire.
4. **Client** (`sdk-android`): `ComposeCastScreen` keeps the player; a live screen opens the
   socket (Ktor client, OkHttp engine), calls `setDocument` for full frames and `updateDocument`
   for data updates, reconnects with backoff, and closes when the screen leaves composition or the
   app goes to the background. Falls back to the plain GET if the socket fails.
5. **Dashboard components are semantic** ([ADR 0015](0015-screen-model-is-the-product.md)):
   `gauge`, `sparkline`/`chart`, `kpi`, `progress` — drawn with RC `Canvas` in `sdk-server/render`,
   their values declared in `Global { }`; never raw draw operations in the screen model.
6. Values set by the app itself (`setUserLocalFloat`) are not used for server data — they stay for
   device-side values (T-020).

## Consequences
- Fast refresh is cheap: the limit is the data source (external API rate), not compose-cast. A
  source that pushes (WebSocket/SSE upstream) can drive updates later without changing the app.
- The server holds one coroutine + render loop per open live screen; renders of the same
  `(screen, params)` should be shared between viewers (one ticker, fan-out) — required before
  dashboards are used by many users.
- The live endpoint is public like `/screens`; draft streams need the preview id (ADR 0027).
- `sdk-android` gains an OkHttp dependency (Ktor `Android` engine has no WebSocket).
- Static screens, goldens and existing apps are untouched.

## Implementation notes (T-047 step 2, 2026-11-24)
- Structure fingerprint = the decoded operations re-encoded (`Operation.write`), replaceable values
  reduced to type + id (`RemoteComposeRenderer.structure`). Equal texts share an id, so data can
  change structure; then a full frame is sent (`LiveFramesTest`).
- A data-update frame is a full render with the flag (same size); it saves state and animation,
  not bytes. A delta with only the data operations is a later optimization.
- One render loop per connection for now; sharing it per `(screen, params)` is a follow-up.
- App (step 3): `ComposeCastClient.liveScreen` (OkHttp engine) + `ComposeCastScreen(live = …)`;
  emulator: one connection per visit, 1 frame/s for a minute, closed on Home/Back, reconnected
  after a server restart. Rendering of the animated demo on the emulator: p50 11 ms, p90 16 ms,
  p99 21 ms — check on a real device before promising "no dropped frames".

## How to verify it still holds
`LiveRouteTest`, `LiveFramesTest`, `LiveDemoTest`: first frame is a full document, the next ones have
`DOC_DATA_UPDATE` and identical ids; static screens have no live route. On the emulator: the demo
`dashboard` screen moves without user action (`adb logcat -s ComposeCast` shows applied updates).
On an RC upgrade re-run the live checks in the upgrade playbook (behaviors 18–20).
