# T-037 — Analytics in the panel (screens, taps, events)

Status: queued (after T-038) · Roadmap: Etap 12 · Depends on: T-007; used by T-038 (A/B results) · Module: server-analytics, app-shell

## Goal
The panel shows screen views, taps per button/action, `track` events, daily active devices and
app versions, with a time range and a per-screen view. No external analytics service is needed
(self-hosted, privacy-friendly).

## Design
- Device: `app-shell` batches events (`screen_view`, `action`, `track`, `experiment_exposure`)
  in memory and on disk, then sends `POST /events` every 30 s or when the app goes to the
  background. Anonymous device id (random UUID, resettable); no personal data.
- Server: append-only `events` table (SQLite; daily aggregation into a `stats_daily` table and
  raw retention configurable, default 90 days). Endpoint rate-limited.
- Panel: charts (screen views, top actions, funnels between two screens later).
- Opt-out: a setting in App Settings and a shell API respected when collecting.

## Acceptance criteria
- Emulator session → events visible in the panel within 1 minute; aggregation tested; disabled
  analytics sends nothing (test).

## Docs to update
New ADR (event model, retention, privacy), README, history.
