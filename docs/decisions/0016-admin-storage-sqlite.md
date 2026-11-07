# 0016 — Admin panel storage: SQLite (supersedes 0011 and 0014)

- Status: Accepted
- Date: 2026-11-07

## Context

The owner fixed the deployment model (README Etap 8): **one VPS = one product**; the admin panel
has user accounts with roles, global App Settings (name, `applicationId`, server URL, brand, logo)
and per-screen draft/live state with history. ADR 0011 (JSON files on disk) and ADR 0014 (snapshot
files) were written for a password-only, screens-only panel; users, sessions, settings and
uploaded logos are relational/binary data that files handle badly. The roadmap was switched to
"SQLite storage (Exposed)" in commit `ba04c0f`; this ADR records that decision and reconciles it
with what is already built.

Already built and kept: the `ScreenDefinition` model and its strict canonical JSON (`ScreenJson`,
ADR 0015); `COMPOSECAST_DATA_DIR/screens/<id>.json` loading (`examples/demo`, review after T-002);
the swappable `LiveScreens` registry (T-003).

## Decision

- Admin data lives in one SQLite file, `$COMPOSECAST_DATA_DIR/compose-cast.db`, accessed with
  Exposed (+ `org.xerial:sqlite-jdbc`). Tables (detail in T-007): `screens` (id, **canonical
  `ScreenJson` text**, status draft/live, updated_by, updated_at), `screen_history` (append-only
  snapshots for rollback, keeps ADR 0014's semantics and `COMPOSECAST_HISTORY_LIMIT`), `users`
  (email, bcrypt hash, role `admin`/`editor`/`viewer`), `sessions`, `app_settings` (single row,
  logo as BLOB).
- **The screen format does not change:** a screen row stores exactly what a `<id>.json` file
  contains, validated by `ScreenDefinition.problems()`. Import/export of `<id>.json` files is part of
  the admin API, so `examples/demo` and hand-written screen directories stay first-class.
- **Two deployment modes**, both served through `LiveScreens`: *files* (`screens/` directory, no
  admin panel, read-only — today's mode) and *admin* (database is the source of truth; publish
  builds a new registry and `replace()`s it). If both exist at first start, the files are imported
  into an empty database once.
- Auth: accounts, not a shared `ADMIN_PASSWORD`. The first admin is bootstrapped from
  `COMPOSECAST_ADMIN_EMAIL` / `COMPOSECAST_ADMIN_PASSWORD` when the `users` table is empty.
- Drafts are persisted (`status = draft`); the preview flow of ADR 0013 (temporary `__preview`
  screen + QR code) is unchanged.

## Consequences

- New dependencies (Exposed, sqlite-jdbc — bundles native libs incl. linux-x86_64/aarch64, check in
  the Docker image, T-009) and a schema migration story (decide in T-007: Exposed
  `MigrationUtils` vs Flyway).
- Backup: copy the `.db` file only via SQLite's online backup (`VACUUM INTO`), not `cp` while running;
  add an admin "download backup" action.
- `curl -u admin:$ADMIN_PASSWORD` examples in ADRs 0013/0014 and old task text are obsolete; the
  API uses session cookies (and later tokens).
- Agents: never write screen content that bypasses `ScreenDefinition` validation; never store a
  non-canonical screen JSON.

## How to verify it still holds

After T-007: `sqlite3 data/compose-cast.db '.tables'` lists the tables above; exporting a screen
and loading the file with `ScreenJson.decode` round-trips; `grep -rn ADMIN_PASSWORD
server-app/src admin*/src` finds nothing.
