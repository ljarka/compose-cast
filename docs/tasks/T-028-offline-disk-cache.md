# T-028 — Offline: disk cache of the last known screen

Status: queued (after T-027) · Roadmap: Etap 7 · Depends on: T-003 (ETag cache) · Module: sdk-android, app-shell

## Goal

Without network access, the app shows the last successfully loaded version of a screen with an
"offline" banner instead of an error. With network access, behavior is unchanged (ETag
revalidation).

## Why

The whole UI comes from the server, so without a cache the app is blank offline. Today the cache
is in memory only (`sdk-android/AGENTS.md`: LRU 32, "Disk cache: Etap 7").

## Design

- Key: screen id plus sorted params plus the RC API level (ADR 0021). Value: document bytes, ETag,
  title, timestamp. Store in `cacheDir/composecast/` with a size cap of 20 MB, LRU by access.
- Flow: request with `If-None-Match` from the disk entry → `304` → bytes from disk. On a network
  error or timeout, if a disk entry exists → show it with `stale = true` (the shell shows a small
  banner plus Retry). Otherwise show the existing error state.
- Brand (`GET /brand`) is cached the same way.
- Never cache responses with `Cache-Control: no-store` (add that header option on the server for
  screens with secrets or personal data: one boolean screen field `noStore`).

## Acceptance criteria

- Unit tests with a fake engine: hit, `304`, offline fallback, size eviction.
- Emulator: load `main`, enable airplane mode, restart the app → `main` shows with the banner. A
  Maestro flow covers it if feasible.
- `./scripts/verify.sh` is green.

## Docs to update

`sdk-android/AGENTS.md`, `app-shell` docs, README (Etap 7 box), history.

## Out of scope

Prefetch, background sync.
