# 0023 — Module structure: small modules, each with its own AGENTS.md

- Status: Accepted (owner, 2026-11-16)
- Date: 2026-11-16

## Context

The owner wants AI agents to work on one part of the project without loading the whole project
into context. The roadmap adds many server features (admin panel, templates, forms, app user auth,
push, analytics, flags, AI). The original T-007 plan put all of the admin panel into `server-app`.
That would make one module (and one `AGENTS.md`) grow without limit.

## Decision

1. **One feature = one Gradle module with its own `AGENTS.md`** (scope, files, public API,
   decisions, how to test). The module `AGENTS.md` stays at **150 lines or fewer**; longer detail
   goes to `docs/`. An agent working in a module reads only: root `AGENTS.md` → the task file →
   that module's `AGENTS.md` (plus ADRs it links).
2. **Planned modules.** The first task that needs a module creates it:

| Module | Scope | Created by |
|---|---|---|
| `server-store` | SQLite/Exposed database, migrations, repositories: screens, history, users, sessions, settings | T-007 |
| `server-admin` | Admin panel HTTP routes and web UI (editor, preview, wizard UI) | T-007 (T-029, T-031, T-032) |
| `app-templates` | Bundled app templates (resources only) and the bundle format | T-030 |
| `server-ai` | AI provider interface and implementations | T-033 |
| `server-forms` | Form submission, validation, targets, submissions storage | T-034 |
| `server-auth` | App user authentication (`AuthProvider`, Supabase) | T-035 |
| `server-push` / `android-push` | Push sending (FCM) / device registration (optional dependency of the shell) | T-036 |
| `server-analytics` | Event ingestion, aggregation, queries | T-037 |
| `server-flags` | Feature flags, experiments, bucketing | T-038 |

3. **Dependency direction:**
   - `server-app` is the composition root: `main`, configuration, wiring, demo screens. It contains
     no feature logic.
   - Feature modules (`server-*` except `server-app`) may depend on `protocol`, `sdk-server`,
     `server-store` and libraries. They **never** depend on `server-app` or on another feature module.
   - `server-admin` is the one exception: it may depend on feature modules to show their admin UI.
   - Each feature module owns its own database tables and migrations, using `server-store`'s
     `Database`.
4. Enforced by `scripts/check-architecture.sh`: R9 (every Gradle module has an `AGENTS.md` of 150
   lines or fewer) and R10 (the dependency direction above).
5. Every task names its module(s) in the status line (`Module: …`).

## Consequences

- More `build.gradle.kts` files. Agents get a small, closed context per task.
- The package root is `io.composecast.server.<module>` (T-007's earlier `cc.composecast` paths are
  wrong).
- Large existing files are split by T-039 (the renderer is about 800 lines).

## How to verify it still holds

`./scripts/check-architecture.sh` passes R9 and R10.
