# 0014 — Admin panel: change history and rollback

- Status: Accepted
- Date: 2026-11-07

## Context

The admin panel allows non-developer users to publish screen changes. A bad change must be
reversible without a code deployment. Two options were evaluated:

1. **Git-based** — every publish commits `data/screens/<id>.json` to the repository. Git history
   is the change log; rollback = `git revert`. Clean, zero extra code, content is auditable.
   Problem: the server process needs a writable git working tree and must shell out to `git`;
   this couples the runtime to the dev toolchain. In a Docker deployment the image is immutable;
   mounting a git repo into a container is fragile.
2. **Append-only JSON snapshot log** — on each publish, the previous version of the screen
   definition is copied to `data/screens/<id>.history/<iso-timestamp>.json` (immutable). The
   current version is always at `data/screens/<id>.json`. Rollback = copy a snapshot back to the
   current file. No git dependency; works in any environment that can write files.

In combination with ADR 0011 (file-based storage), the snapshot log fits naturally: the same
volume mount that holds current screens also holds history. The number of history entries per
screen is bounded by a configurable `COMPOSECAST_HISTORY_LIMIT` env var (default: 50).

## Decision

**Append-only JSON snapshot log** per screen:

```
data/screens/
  home.json                         ← current (live)
  home.history/
    2026-11-07T14:32:00Z.json       ← immutable past version
    2026-11-07T13:10:00Z.json
    ...
```

- Publish flow: read current → copy to `history/` → write new version → respond 200.
- Rollback flow: read chosen snapshot → copy to current → respond 200.
- History pruning: after each publish, if `history/` has > `COMPOSECAST_HISTORY_LIMIT` entries,
  delete the oldest ones.
- The admin API exposes:
  - `GET  /admin/api/screens/{id}/history` — list of `{timestamp, sizeBytes}` entries.
  - `POST /admin/api/screens/{id}/rollback/{timestamp}` — restore that snapshot.

## Consequences

- No git dependency at runtime; the Docker image stays stateless except for `data/` volume.
- History is lost if `data/` is deleted; backup policy applies (same as ADR 0011).
- Agents: never write directly to `<id>.history/`; always go through the storage layer to
  ensure pruning and consistency.

## How to verify it still holds

```bash
# Publish twice, check history has 1 entry
curl -u admin:$ADMIN_PASSWORD -X PUT http://localhost:8080/admin/api/screens/test \
  -H 'Content-Type: application/json' -d '{"id":"test","title":"v1","components":[]}'
curl -u admin:$ADMIN_PASSWORD -X PUT http://localhost:8080/admin/api/screens/test \
  -H 'Content-Type: application/json' -d '{"id":"test","title":"v2","components":[]}'
ls data/screens/test.history/  # should have exactly one file
curl -u admin:$ADMIN_PASSWORD http://localhost:8080/admin/api/screens/test/history
```
