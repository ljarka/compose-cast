# T-053 — SQLite schema migrations, foreign keys, and expired session cleanup

Status: done · Roadmap: quality · Depends on: — · Module: server-store, server-app

## Goal

Make `compose-cast.db` safe to evolve across server upgrades, enforce SQLite foreign keys, and
actually prune expired admin sessions in production.

## Why

- `StoreDatabase.open` currently relies only on `SchemaUtils.create(...)`, which cannot alter
  existing tables when future versions change columns.
- `Tables.kt` defines `Sessions.userId.references(Users.id)`, but SQLite disables foreign-key
  enforcement unless `PRAGMA foreign_keys = ON` is set per connection.
- `UserStore.deleteExpiredSessions()` exists and is tested, but is never called at runtime.

## Design

- Enable `PRAGMA foreign_keys = ON` in `StoreDatabase.open` (`setupConnection`).
- Introduce versioned migrations tracked via `PRAGMA user_version` (starting at version 1 for the
  current schema), with a unit test verifying upgrading a pre-versioned (`user_version = 0`) DB.
- Call `deleteExpiredSessions()` on startup in admin mode and periodically in a background coroutine
  bound to the Ktor application lifecycle (e.g. every hour).

## Steps (files to touch)

1. `server-store/.../StoreDatabase.kt`: enable `PRAGMA foreign_keys = ON` and run versioned
   migrations (`PRAGMA user_version`).
2. `server-store/src/test/...`: test foreign-key enforcement and schema migration from version 0.
3. `server-app/.../Main.kt` (or `AdminPanel.kt`): prune expired sessions on startup and periodically.

## Acceptance criteria

- `PRAGMA foreign_keys` is `1` on connections opened by `StoreDatabase`.
- Opening an existing DB (`user_version = 0`) migrates cleanly to `user_version = 1` without data
  loss; opening a newer DB version fails fast with a clear error.
- Expired sessions are pruned automatically in admin mode.
- `./scripts/verify.sh` is green.

## Verification

`./scripts/verify.sh`

## Docs to update

`server-store/AGENTS.md`, `server-app/AGENTS.md`, `docs/history.md`.

## Out of scope

Changing the current table layout.
