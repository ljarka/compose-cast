# 0009 — Documentation structure for AI agents

- Status: Accepted
- Date: 2026-11-07

## Context
The project is maintained mostly by AI agents over a long time. A single growing `AGENTS.md`
(history, gotchas, decisions mixed) gets harder to use with every task; agents re-learn or
contradict earlier findings.

## Decision
Knowledge has exactly one home:
- `AGENTS.md` (root): short index — rules, commands, module map, where to find what.
- `<module>/AGENTS.md`: scope, key files, module-level decisions of that module.
- `docs/decisions/NNNN-*.md`: one decision per file (why), superseded rather than edited away.
- `docs/remote-compose.md`: verified behavior of the RC library per version.
- `docs/history.md`: what was done when (short, links to commits/ADRs).
- `README.md`: humans — vision, screenshots, quick start, roadmap (the plan).
Definition of Done for every task includes updating these. `scripts/check-docs.sh` (CI) checks
that every module has an `AGENTS.md`, relative links resolve and every ADR is indexed.

## Consequences
Root `AGENTS.md` stays short (target < 150 lines); detail goes to the file that owns it.

## How to verify it still holds
`./scripts/check-docs.sh`; `wc -l AGENTS.md`.
