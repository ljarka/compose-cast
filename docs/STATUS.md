# compose-cast — Project Status

_Auto-maintained. Last updated: 2026-11-07._

## Current Stage

**Etap 2** — Core SDK + Server foundation

## Task Queue

| Task | Status | Note |
|---|---|---|
| T-001 Typed actions | ✅ done (`4bce343`) | `Action` + `ActionCodec` in protocol |
| T-002 Serializable screen model + JSON screens | ✅ done (`2918209`) | `ScreenDefinition`, JSON loading, validation |
| **T-003 Per-request rendering, route params, ETag** | **▶ ready** | Next task to implement |
| T-004 Version negotiation | ⏳ queued | After T-003 |
| T-005 iOS spike | 🚫 blocked | Needs Xcode on dev machine |
| T-006 Data sources | ⏳ needs design | After T-003 |
| T-007 Admin panel (Etap 8) | ⏳ needs design | ADRs 0011–0014 done; after T-002; static docs only |
| T-008 Local state spike | ⏳ queued | Investigate RC state API in procedural JVM context |

## Pending Decisions

None — ADRs 0011–0014 written, T-007 unblocked for static-document scope.

## Architecture

```
Ktor server → RemoteComposeRenderer → binary .rc (HTTP octet-stream)
                                                    ↓
                               Android RemoteComposePlayer (View)
```

Wire format: **binary `.rc` only**. JSON is internal (admin panel ↔ server). Never JSON on the wire.

## Key Files

| Purpose | File |
|---|---|
| Entry point for Claude Code | `CLAUDE.md` |
| Architecture decisions (14 ADRs) | `docs/decisions/` |
| Remote Compose knowledge base | `docs/remote-compose.md` |
| Task queue | `docs/tasks/README.md` |
| Change log | `docs/history.md` |

## Recent Changes

- `aa710fa` — T-008 local state spike added; T-007 scoped to static docs
- `2918209` — T-002 done: serializable screen model + JSON screens
- `7f8d534` — RC official docs: component catalog, state API, comparison table
- `4bce343` — T-001 done: typed actions (Action + ActionCodec)
