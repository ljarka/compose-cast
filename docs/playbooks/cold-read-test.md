# Playbook — cold-read test of the agent docs

Proves that a fresh agent can find the next task and do it from the repository alone. Run it after
larger documentation changes and every few tasks; docs rot silently otherwise.

1. Commit the current state (the test runs on a clean checkout).
2. Start a **fresh** agent (no conversation context) in an isolated git worktree, with the prompt
   below. It must not modify tracked files.
3. Fix every finding in the docs/tasks/scripts — not in the agent's head. Record the run in
   `docs/history.md` (date, confidence score, main findings).

```text
You are an AI coding agent who has never seen this repository before. The owner has given you NO
specific request — figure out from the repository alone what to work on next and exactly how.
This is an onboarding test of the documentation: do NOT modify, create or delete tracked files,
do not commit. You may read anything and run the verification commands the docs mention.
Work the way the repo's own instructions tell a new agent to work. Then report:
1. Files read, in order, and what pointed you there. 2. Commands run and outcomes.
3. What you would work on next and why. 4. Your step-by-step plan (files, design, tests,
verification incl. device/screenshot checks, docs to update). 5. Everything unclear,
contradictory, outdated, missing or guessed — with file paths and lines; code that disagrees
with docs. Be critical. 6. Confidence 0–10 that you could finish without asking, and what would
raise it.
```

Note: the harness may inject a cached copy of `AGENTS.md` from when the session started; the
file on disk is authoritative.
