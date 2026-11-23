# T-042 — Reload the data directory without a restart

Status: done (`383c443`) · Roadmap: Etap 6 · Depends on: — · Module: server-app

## Goal

Editing `screens/*.json` or `brand.json` takes effect without `docker compose restart`. Found by
the weather app (T-025): an edited screen silently kept serving the old version until the restart.

## Design

- `server-app`: a background coroutine checks the data dir every 2 s (newest mtime + file list;
  polling works on every Docker bind mount, inotify does not reliably). On change: debounce 500 ms,
  `loadScreens(dir)` off the request path; success → `LiveScreens.replace()` + log "reloaded N
  screens"; failure → keep serving the previous registry and log the error with file + JSON path.
- Env `COMPOSECAST_RELOAD=true|false` (default `true`).
- The admin panel (T-007) will call the same `replace()`; keep the reload function reusable.

## Acceptance criteria

- `RoutingTest`/new test: change a file in a temp data dir → next request serves the new document;
  an invalid file → old document still served, error logged.
- Manual: weather app in Docker, edit a title, `curl /screens/main/debug` shows it within ~3 s.

## Docs to update

`server-app/AGENTS.md` (Rules: "nothing swaps it yet" changes), install-server env table,
`new-app-repository.md` (drop the restart note), history.

## Result (2026-11-23)

- `server-app/DataDirReloader.kt`: polling on a daemon thread (no coroutine scope needed), every
  2 s, fingerprint = relative path + mtime + size of every file; on change waits 500 ms, then
  `loadScreens` + `LiveScreens.replace()`; a failed version is logged once and not retried until the
  files change again. `COMPOSECAST_RELOAD=false` disables it; nothing to do without a data dir.
- Test: `DataDirReloaderTest` (edit, add, delete, invalid file keeps the old screens, fix).
- A reload re-renders every screen and re-fetches its images (new registry = new image cache).
- Manual (weather app in Docker, local image): edited `cities.json` title served after 2 s; an
  invalid file logged, previous screen still served; restoring it reloaded again.
