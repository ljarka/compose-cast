# 0010 — Executable rules, one verify command, playbooks, task queue

- Status: Accepted
- Date: 2026-11-07

## Context
ADR 0009 put knowledge in the right files, but (1) rules written only as text get broken silently,
(2) agents had to remember four verification commands and `JAVA_HOME`, (3) recurring procedures
were re-invented each time, (4) a fresh agent knew *how* to work but not *what* to do next.

## Decision
- `scripts/check-architecture.sh`: rules R1–R8 (RC API confinement, no RC types in public API,
  pure `protocol`, no `kotlin-android` plugin, Compose via BOM, versions in the catalog,
  `server-scripting` without Ktor). Each failure names the ADR and the fix.
- `scripts/verify.sh` (`--quick`): the single verification entry point; CI runs exactly it.
- `docs/playbooks/`: step-by-step procedures (add component, RC upgrade, dependency upgrade,
  emulator verification).
- `docs/tasks/`: ordered task queue; each task has goal, why, design, files, acceptance criteria,
  verification, docs. Without an owner request an agent takes the first `ready` task. Finishing a
  task includes specifying the next one.
- `scripts/check-docs.sh` also checks that playbooks and tasks are indexed.

## Consequences
Changing a rule = change the ADR and the script together. Tasks in the queue may go stale — the
agent working one updates it first when reality differs.

## How to verify it still holds
`./scripts/verify.sh`; CI step "Verify"; mutating code to break a rule makes
`check-architecture.sh` fail (verified for R1, R3, R5, R7 on 2026-11-07).
