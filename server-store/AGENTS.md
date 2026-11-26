# server-store — AGENTS.md

The admin database (T-007, [ADR 0016](../docs/decisions/0016-admin-storage-sqlite.md)): one SQLite
file, `$COMPOSECAST_DATA_DIR/compose-cast.db`, accessed with Exposed 1.x. Used only in **admin
mode** (`COMPOSECAST_MODE=admin`, wired in `server-app`); files mode never opens it.

## Files

| File | Purpose |
|---|---|
| `StoreDatabase.kt` | `StoreDatabase.open(file)`: connects (WAL, `busy_timeout` 5 s, `foreign_keys = ON`), runs `PRAGMA user_version` migrations (T-053); `tx {}`, `createTables()`, `ping()`, `schemaVersion()` |
| `Tables.kt` | `Documents`/`DocumentHistory` table classes, instantiated as `screens` (id, draft_json, live_json, timestamps, published_by) + `screen_history` (append-only, `screen_id`) and `components` + `component_history` (`component_id`, T-049), `app_settings` (key/value), `users` (unique email, bcrypt hash, role text), `sessions` (id = token hash, user, expiry) |
| `ScreenStore.kt` | `list`, `get`, `live()` (what apps are served), `saveDraft`, `discardDraft`, `publish`, `publishAll` (one transaction: first-start import), `history`, `revision`, `rollback` (= publish an old revision again; keeps a pending draft); `ComponentStore` = the same over the component tables (T-049, never parses JSON either) |
| `UserStore.kt` | Admin accounts + sessions (A2): `list`, `findById`, `findByEmail`, `create`, `createIfNone` (atomic first-admin bootstrap), `updateRole`, `updatePassword` (revokes other sessions), `delete` (last-admin guard), `createSession` (records `last_login_at`), `sessionUser` (deletes an expired one), `deleteSession`, `deleteExpiredSessions`; stores bcrypt hashes and session-id hashes made by `server-admin` |
| `SettingsStore.kt` | `settings[key]` get/set; `BRAND` = the brand as `brand.json` text, `HOME_SCREEN` = screen id served for `main` |

## Decisions

- **The store never parses screen JSON.** Callers validate (`ScreenDefinition.problems()`) and pass
  canonical `ScreenJson.encode` text; the module depends on no compose-cast module (ADR 0023: feature
  modules may depend on it, never the other way round).
- **History is append-only:** a rollback publishes the old JSON again as a new row (ADR 0014 semantics).
- Timestamps are ISO-8601 UTC strings (`Instant.toString()`); `Clock` injectable for tests. Session
  expiries are truncated to whole seconds so the strings compare chronologically in SQL.
- Exposed 1.x packages are `org.jetbrains.exposed.v1.core` / `.v1.jdbc` (`eq`, `and` are top-level
  functions; `SchemaUtils.create` = `CREATE TABLE IF NOT EXISTS`). Verified in the 1.6.1 sources
  jar, 2026-11-23. SQLite does not enforce `varchar` lengths; Exposed checks them before writing.
- Schema changes (T-053): tracked via `PRAGMA user_version` (`SCHEMA_VERSION = 2`; v2 adds `users.last_login_at`). Opening a DB
  with a newer version fails fast; pre-versioned (`0`) DBs migrate cleanly in one transaction.
- Feature modules (forms, analytics, …) define their own Exposed `Table`s and call
  `createTables()` / `tx {}` (Exposed is an `api` dependency for that).

## Tests

`ScreenStoreTest`, `UserStoreTest` (temp file per test: drafts vs. live, publish/history, `publishAll` atomicity,
rollback, reopen). `server-app`'s `AdminScreensTest` covers the import + byte-identical serving.

```bash
./gradlew :server-store:test
```
