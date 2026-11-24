# 0030 — Live draft previews: one preview per editor and screen, updated on every save

- Status: Accepted
- Date: 2026-11-24
- Amends: [0027](0027-draft-preview-ids.md) (id lifetime and reuse), [0013](0013-admin-preview.md);
  builds on [0029](0029-live-screens-websocket-data-updates.md) (live screens)

## Context

T-032: a phone showing a draft should follow the panel while the owner edits, without scanning a
QR code again. ADR 0027 gave every "Preview on phone" click a new `__preview-…` id that showed a
frozen copy for 30 minutes. T-047 added live screens: a WebSocket per screen that pushes changes
and a registry looked up on every tick, so a replaced registry reaches open phones.

## Decision

- **One preview id per (admin user, screen)** (`PreviewScreens.open`): clicking "Preview on phone"
  again keeps the id (same QR code, same link); another editor or another screen gets its own.
  The id is still 128 random bits and still the only secret (ADR 0027).
- **Every saved draft updates the open preview in place** (`ScreenPublishing.updatePreview`, from
  the JSON editor's save and every visual-editor edit). No open preview: nothing happens. A draft
  that saves but does not build (e.g. a missing secret) keeps the last preview and is logged.
- **Previews are live** (`AdminScreens.preview`): served with `live.everyMs = 500` (or the draft's
  own faster `live`), without changing the stored draft. The Preview app opens the stream like any
  live screen; no new endpoint and no new app code beyond a "Preview ·" title.
- **A new registry is always a full frame** (`LiveFrames`): an edited text sent as a data update
  would keep its old width (remote-compose.md behavior 20). This applies to publishes too.
- **Lifetime**: 30 minutes after the **last** save or click (sliding); **logout revokes** all of
  the user's previews at once (open phones get "not found" and keep the last frame). Restart: gone.

Not done (decide when needed): an explicit "Stop preview" button, previews of other screens'
drafts when navigating from a preview (navigation still goes to the published screens).

## Consequences

- Edit → phone in about one second (500 ms tick + render + frame), measured 1.2 s on the emulator.
- Each open preview re-renders every 500 ms while a phone is connected; a preview with external
  data sources fetches them per their `cacheSeconds`.
- A shared preview link keeps showing new edits while the editor works — it is an unlisted live
  view of the drafts of that screen until logout or 30 minutes of inactivity.

## How to verify it still holds

`PreviewScreensTest` (stable id, update, revoke, expiry), `AdminScreensTest` (`an edit saved in the
panel reaches a phone … within a second`: same QR on a second click, full frame with the edit in
< 2 s, logout closes the stream and GET answers 404), `ScreenPagesTest`/`VisualPagesTest` (saves
notify the preview, previewing saves nothing); on a device: `scripts/verify-live-preview.sh`.
