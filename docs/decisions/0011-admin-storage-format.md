# 0011 — Admin panel: storage format

- Status: Superseded by [0016](0016-admin-storage-sqlite.md) (the `<id>.json` file format and data-dir layout remain as the *files* deployment mode and import/export)
- Date: 2026-11-07

> Review 2026-11-07: the "metadata, draft/live status" inside `<id>.json` conflicts with ADRs
> 0013/0014 and the strict `ScreenJson` format (T-002) — see
> [T-007 review notes](../tasks/T-007-admin-panel.md#review-notes-2026-11-07--reconcile-before-detailing-the-steps).

## Context

The admin panel (T-007, Etap 8) needs to persist screen definitions created by non-developer
users. Two realistic options:

1. **JSON files on disk** — one `screens/<id>.json` per screen; git-friendly, trivially backed up,
   human-readable, no schema migrations, works on any VPS without extra processes.
2. **Embedded SQLite via Exposed** — relational queries, easy pagination, schema migrations with
   Flyway/Liquibase; adds a dependency and a migration story; no real benefit at the scale
   expected (dozens of screens, one instance).

No multi-instance deployment is planned. Concurrent writes within a single Ktor instance are
serialised by a `Mutex` on the storage layer.

## Decision

JSON files on disk:

```
data/
  screens/
    <id>.json          ← screen definition (components, metadata, draft/live status)
    <id>.history/
      <timestamp>.json ← immutable snapshots for rollback (ADR 0014)
```

The `data/` directory is configurable via the `COMPOSECAST_DATA_DIR` env var (default `./data`);
it is volume-mounted in Docker deployments.

`ScreenDefinition` is a `@Serializable` data class in `protocol`; `admin-server` reads/writes it
via `kotlinx.serialization.json`; `sdk-server` renders it to `.rc` bytes.

## Consequences

- **Easy backup:** `cp -r data/ backup/` is sufficient.
- **Concurrent access:** a `Mutex` per screen id prevents torn writes; safe for one instance.
- **Multi-instance** (future): needs a shared volume or a migration to a real DB — write ADR
  before implementing.
- **schema change** in `ScreenDefinition`: use `@SerialName` + `@Required(false)` + sensible
  defaults; document in a migration note in `docs/history.md`.
- Agents: `ScreenDefinition` lives in `protocol`; `admin-server` never imports `sdk-server`'s
  DSL types directly.

## How to verify it still holds

```bash
ls data/screens/        # screen json files present after admin CRUD
grep -r "Exposed\|SQLite" admin-server/build.gradle.kts && echo "VIOLATION" || echo "ok"
```
