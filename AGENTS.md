# compose-cast — AGENTS.md

Server-driven UI framework: the **whole app UI is defined on the server** (Kotlin DSL now, admin
panel later), data comes from external HTTP APIs bound on the server, and apps play the result
natively. **Android only** ([ADR 0022](docs/decisions/0022-android-only.md)): **Remote Compose**
documents played by the RC player. Plan and vision: [README](README.md#vision).

This file is the index. Keep it short (< 150 lines); details live where the table below says.

## Start here (first time in this repo)

1. Read this file, then [docs/tasks/README.md](docs/tasks/README.md).
2. Run `./scripts/verify.sh` (it finds the JDK and Android SDK itself; `--quick` skips Android and
   screenshots — enough only for server-only work). It must be green before you change anything;
   if not, fixing it is your first task.
3. The owner's request wins. Without one, take the **first `ready` task** in the queue and follow its
   file; use the [playbooks](docs/playbooks/README.md) for recurring procedures.
4. Before touching an area, read its module `AGENTS.md` and the ADRs it links.
5. Finish with the Definition of Done below; leave the next task specified for the next agent.

## Where knowledge lives

| Need | Read |
|---|---|
| **What to do next, exactly** | [docs/tasks/](docs/tasks/README.md) (task queue) |
| One-page overview (generated, never edit) | [docs/STATUS.md](docs/STATUS.md) — `./scripts/gen-status.sh` |
| How to do recurring things (add component, upgrades, emulator) | [docs/playbooks/](docs/playbooks/README.md) |
| Vision, principles, roadmap | [README → Vision, Roadmap](README.md#vision) |
| Why something is the way it is | [docs/decisions/](docs/decisions/README.md) (ADRs) |
| Screen JSON format (every component, modifier, binding) | [docs/screen-json-format.md](docs/screen-json-format.md) |
| How Remote Compose actually behaves (per version) | [docs/remote-compose.md](docs/remote-compose.md) |
| New RC release? (watch it: alpha, semantics change) | `./scripts/check-rc-release.sh`, [release notes](https://developer.android.com/jetpack/androidx/releases/compose-remote), [upgrade playbook](docs/playbooks/upgrade-remote-compose.md) |
| What was done when | [docs/history.md](docs/history.md) |
| A module's scope, files, decisions | `<module>/AGENTS.md` |

Do not load the whole project: root `AGENTS.md` → task → the modules in its `Module:` line
([ADR 0023](docs/decisions/0023-module-structure.md)).

## Module map

| Module | Scope | Status |
|---|---|---|
| `protocol` | Screen model `ScreenDefinition` + `ScreenJson`, `Action` + codec, `Headers`, `BrandConfig` | done |
| `sdk-server` | DSL/JSON → `ScreenDefinition` → RC documents (`RemoteComposeRenderer`), `ScreenRegistry` | done |
| `server-scripting` | `.screen.kts` hot reload | empty |
| `server-app` | Ktor server; demo screens in `demo/` (`demoScreens()` in `Screens.kt`) | done |
| `sdk-android` | Fetch RC documents, play with `remote-player-view` (`ComposeCastScreen`) | done |
| `app-shell` | The Android app itself (activity, back stack, states, action routing), configured via resources | done |
| `app-template` | Demo app built like an app repository: `io.composecast.app` + `compose-cast.properties` | done |
| `gradle-plugins` (included build) | `io.composecast.app` Gradle plugin: properties → branded app + shell | done |
| `compat-tests` | Screenshot tests server → player; goldens = README images | done |
| planned: `server-store`, `server-admin`, `app-templates`, `server-ai`, `server-forms`, `server-auth`, `server-push`/`android-push`, `server-analytics`, `server-flags` | one feature = one module with its own `AGENTS.md` ([ADR 0023](docs/decisions/0023-module-structure.md)) | planned |

## Rules

1. **Remote Compose stays the Android wire format** — never add a custom Android renderer
   ([0001](docs/decisions/0001-remote-compose-wire-format.md)).
2. RC creation API only in `sdk-server/.../render/` (`RemoteComposeRenderer`); RC player API only in `sdk-android`; no RC
   types in public APIs ([0002](docs/decisions/0002-confine-restricted-remote-compose-api.md)).
3. **Never guess RC behavior** — read the sources jar ([how](docs/remote-compose.md#how-to-research-the-api-do-this-instead-of-guessing))
   and record what you verified in `docs/remote-compose.md` with the version.
4. Every visual change keeps screenshot tests green or re-records them after **looking at the PNGs**
   ([0006](docs/decisions/0006-screenshot-tests-as-goldens.md)). Every new component gets one.
5. Verify on a device/emulator for anything rendering-related; unit tests have missed every
   rendering bug so far.
6. Changing an accepted decision = new ADR that supersedes it. Architecture rules are enforced by
   `scripts/check-architecture.sh` ([0010](docs/decisions/0010-executable-rules-verify-tasks.md)).
7. Git: work on `main` (or the branch/worktree you were given). **Commit** finished, verified work
   (`verify.sh` green) without asking: small commits, conventional messages (`feat:`, `docs:`,
   `fix:`). **Push only when the owner asks** (owner, 2026-11-16).
8. **Never commit sensitive data:** no secrets, `.env`, keystores, real API keys, personal data, and
   no AI chat histories or tool state (`.claude/`, `.copilot/`, `copilot/`, `.obsidian/`, all
   gitignored). Before every commit, check `git status` and stage files by name, never `git add -A`
   or `git add .`.

## Definition of Done (every task)

- [ ] `./scripts/verify.sh` green (same as CI: docs, architecture, formatting, builds, lint, screenshots).
- [ ] Module `AGENTS.md` updated (files, decisions) for every module touched.
- [ ] New decision → ADR; new RC fact → `docs/remote-compose.md`; entry in `docs/history.md`.
- [ ] README roadmap boxes ticked / adjusted; README screenshots current.
- [ ] Task queue updated (then `./scripts/gen-status.sh`): task `done` (add the commit hash after committing), next
  task specified enough to start.

## Environment

- JDK 21 (Homebrew `openjdk@21`). `scripts/verify.sh` finds it; for raw `./gradlew` in agent
  shells (no `~/.zshrc`): `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- Android SDK `~/Library/Android/sdk` (`local.properties` is gitignored; `verify.sh` falls back to
  `~/Library/Android/sdk` or `~/Android/Sdk`); AVD `Medium_Phone_API_37.0`, start it with
  `-gpu host` (≈20x faster). On Linux `verify.sh` also probes `/usr/lib/jvm/*21*`.
- UI clicking on the emulator: **Maestro** (`/opt/homebrew/bin/maestro`, 2.12.1), flows in
  `app-template/maestro/` ([playbook](docs/playbooks/verify-on-emulator.md)).
- `gh` CLI (`/opt/homebrew/bin/gh`, logged in as the owner, scope `repo`): CI runs
  (`gh run list/watch`), releases. https://github.com/ljarka/compose-cast/actions
- Toolchain: AGP 9.5.1 (built-in Kotlin: never apply `org.jetbrains.kotlin.android`), Gradle 9.6.0,
  Kotlin 2.4.21, Compose BOM (`platform(libs.compose.bom)`), compileSdk 37 / targetSdk 36,
  Ktor 3.6.1, RC 1.0.0-alpha23. All versions in `gradle/libs.versions.toml`.

## Commands

```bash
./scripts/verify.sh                               # = CI (.github/workflows/ci.yml)
./scripts/verify.sh --quick                       # docs + architecture + formatting + JVM build/tests
./scripts/verify.sh --docker                      # server image + distribution/ install smoke test (CI job `docker`)
./gradlew spotlessApply                           # fix formatting (ktlint, rules in .editorconfig)
./gradlew :compat-tests:recordRoborazziDebug      # accept intended visual changes (look at PNGs!)
./scripts/verify-app-repository.sh [--install]    # publish dry run + build distribution/android-app (no Kotlin)

# Server + debugging (endpoints: server-app/AGENTS.md)
docker compose up -d                              # image from server-app/Dockerfile, examples/demo
./gradlew :server-app:run
curl localhost:8080/screens/main/debug            # decoded RC document tree

# Android end to end: docs/playbooks/verify-on-emulator.md
maestro test app-template/maestro/smoke.yaml      # click through the demo app (emulator + server running)
```

## Current focus

[Task queue](docs/tasks/README.md) (priority order, re-prioritized 2026-11-16): T-025 Milestone 1 weather app
(in progress: `v0.1.0` released, app in [ljarka/weather-app](https://github.com/ljarka/weather-app), **waits for owner: domain**) →
**T-044 (gaps found by the weather app)** → **T-007 admin panel MVP → T-029 visual editor (the core of the product:
anyone builds app screens in the admin panel)** → T-031 wizard → T-030 templates → T-032 live
preview → T-038 flags/A-B → T-037 analytics → T-033 AI → T-027 SDK-loaded images → T-028 offline cache → T-018 → T-021 → T-034 forms → T-035 Supabase auth → T-036 push → T-017.
Real apps decide the catalog; T-019/T-020/T-022 are `later`. Android only
([ADR 0022](docs/decisions/0022-android-only.md)).
