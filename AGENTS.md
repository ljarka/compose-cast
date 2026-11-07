# compose-cast — AGENTS.md

Server-driven UI framework: the **whole app UI is defined on the server** (Kotlin DSL now, admin
panel later), data comes from external HTTP APIs bound on the server, and apps play the result
natively. Android: **Remote Compose** documents played by the RC player. iOS: being decided
([ADR 0007](docs/decisions/0007-ios-strategy-spike.md)). Plan and vision: [README](README.md#vision).

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
| How to do recurring things (add component, upgrades, emulator) | [docs/playbooks/](docs/playbooks/README.md) |
| Vision, principles, roadmap | [README → Vision, Roadmap](README.md#vision) |
| Why something is the way it is | [docs/decisions/](docs/decisions/README.md) (ADRs) |
| How Remote Compose actually behaves (per version) | [docs/remote-compose.md](docs/remote-compose.md) |
| What was done when | [docs/history.md](docs/history.md) |
| A module's scope, files, decisions | `<module>/AGENTS.md` |

Do not load the whole project.

## Module map

| Module | Scope | Status |
|---|---|---|
| `protocol` | Shared constants (`Actions`, `Headers`), `BrandConfig`, `BrandTokens` | done |
| `sdk-server` | DSL → RC documents (`RemoteComposeRenderer`), `ScreenRegistry`, `ImageLoader` | done |
| `server-scripting` | `.screen.kts` hot reload | empty |
| `server-app` | Ktor server; demo screens in `Screens.kt` (`demoScreens()`) | done |
| `sdk-android` | Fetch RC documents, play with `remote-player-view` (`ComposeCastScreen`) | done |
| `app-template` | Demo Android app: navigation, back stack, error state | done |
| `compat-tests` | Screenshot tests server → player; goldens = README images | done |

## Rules

1. **Remote Compose stays the Android wire format** — never add a custom Android renderer
   ([0001](docs/decisions/0001-remote-compose-wire-format.md)).
2. RC creation API only in `RemoteComposeRenderer.kt`; RC player API only in `sdk-android`; no RC
   types in public APIs ([0002](docs/decisions/0002-confine-restricted-remote-compose-api.md)).
3. **Never guess RC behavior** — read the sources jar ([how](docs/remote-compose.md#how-to-research-the-api-do-this-instead-of-guessing))
   and record what you verified in `docs/remote-compose.md` with the version.
4. Every visual change keeps screenshot tests green or re-records them after **looking at the PNGs**
   ([0006](docs/decisions/0006-screenshot-tests-as-goldens.md)). Every new component gets one.
5. Verify on a device/emulator for anything rendering-related; unit tests have missed every
   rendering bug so far.
6. Changing an accepted decision = new ADR that supersedes it. Architecture rules are enforced by
   `scripts/check-architecture.sh` ([0010](docs/decisions/0010-executable-rules-verify-tasks.md)).
7. Git: work on `main` (or the branch/worktree you were given). **Commit/push only when the owner
   asks** — finishing a task does not imply a commit.

## Definition of Done (every task)

- [ ] `./scripts/verify.sh` green (same as CI: docs, architecture, builds, lint, screenshots).
- [ ] Module `AGENTS.md` updated (files, decisions) for every module touched.
- [ ] New decision → ADR; new RC fact → `docs/remote-compose.md`; entry in `docs/history.md`.
- [ ] README roadmap boxes ticked / adjusted; README screenshots current.
- [ ] Task queue updated: task `done` (add the commit hash once the owner had it committed), next
  task specified enough to start.

## Environment

- JDK 21 (Homebrew `openjdk@21`). `scripts/verify.sh` finds it; for raw `./gradlew` in agent
  shells (no `~/.zshrc`): `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- Android SDK `~/Library/Android/sdk` (`local.properties` is gitignored; `verify.sh` falls back to
  `~/Library/Android/sdk` or `~/Android/Sdk`); AVD `Medium_Phone_API_37.0`, start it with
  `-gpu host` (≈20x faster). On Linux `verify.sh` also probes `/usr/lib/jvm/*21*`.
- No Xcode on the dev machine yet (needed for iOS); no `gh` CLI — CI:
  https://github.com/ljarka/compose-cast/actions (public API, rate-limited).
- Toolchain: AGP 9.5.1 (built-in Kotlin: never apply `org.jetbrains.kotlin.android`), Gradle 9.6.0,
  Kotlin 2.4.21, Compose BOM (`platform(libs.compose.bom)`), compileSdk 37 / targetSdk 36,
  Ktor 3.6.1, RC 1.0.0-alpha23. All versions in `gradle/libs.versions.toml`.

## Commands

```bash
./scripts/verify.sh                               # = CI (.github/workflows/ci.yml)
./scripts/verify.sh --quick                       # docs + architecture + JVM build/tests
./gradlew :compat-tests:recordRoborazziDebug      # accept intended visual changes (look at PNGs!)

# Server + debugging (endpoints: server-app/AGENTS.md)
./gradlew :server-app:run
curl localhost:8080/screens/main/debug            # decoded RC document tree

# Android end to end: docs/playbooks/verify-on-emulator.md
```

## Current focus

[Task queue](docs/tasks/README.md): T-001 typed actions → T-002 JSON screen model → T-003
per-request rendering → T-004 version negotiation (README roadmap Etap 2). T-005 iOS spike is
blocked until Xcode is installed.
