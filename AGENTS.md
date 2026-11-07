# compose-cast — AGENTS.md

Server-driven UI framework: the **whole app UI is defined on the server** (Kotlin DSL now, admin
panel later), data comes from external HTTP APIs bound on the server, and apps play the result
natively. Android: **Remote Compose** documents played by the RC player. iOS: being decided
([ADR 0007](docs/decisions/0007-ios-strategy-spike.md)). Plan and vision: [README](README.md#vision).

This file is the index. Keep it short (< 150 lines); details live where the table below says.

## Where knowledge lives

| Need | Read |
|---|---|
| What to build next, vision, principles | [README → Vision, Roadmap](README.md#vision) |
| Why something is the way it is | [docs/decisions/](docs/decisions/README.md) (ADRs) |
| How Remote Compose actually behaves (per version) | [docs/remote-compose.md](docs/remote-compose.md) |
| What was done when | [docs/history.md](docs/history.md) |
| A module's scope, files, decisions | `<module>/AGENTS.md` |

Start a task by reading: this file → the module's `AGENTS.md` → ADRs that touch the area.
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
6. Changing an accepted decision = new ADR that supersedes it.
7. Git: work on `main`; commit/push only when the owner asks.

## Definition of Done (every task)

- [ ] CI-equivalent build green (commands below), screenshots verified.
- [ ] Module `AGENTS.md` updated (files, decisions) for every module touched.
- [ ] New decision → ADR; new RC fact → `docs/remote-compose.md`; entry in `docs/history.md`.
- [ ] README roadmap boxes ticked / adjusted; README screenshots current.
- [ ] `./scripts/check-docs.sh` passes.

## Environment

- JDK 21 (Homebrew `openjdk@21`). Agent shells don't load `~/.zshrc`:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21` before `./gradlew`.
- Android SDK `~/Library/Android/sdk` (`local.properties`, gitignored); AVD `Medium_Phone_API_37.0`,
  start it with `-gpu host` (≈20x faster).
- No Xcode on the dev machine yet (needed for iOS); no `gh` CLI — CI:
  https://github.com/ljarka/compose-cast/actions (public API, rate-limited).
- Toolchain: AGP 9.5.1 (built-in Kotlin: never apply `org.jetbrains.kotlin.android`), Gradle 9.6.0,
  Kotlin 2.4.21, Compose BOM (`platform(libs.compose.bom)`), compileSdk 37 / targetSdk 36,
  Ktor 3.6.1, RC 1.0.0-alpha23. All versions in `gradle/libs.versions.toml`.

## Commands

```bash
# CI equivalent (.github/workflows/ci.yml)
./gradlew :protocol:build :sdk-server:build :server-scripting:build :server-app:build
./gradlew :sdk-android:assembleDebug :sdk-android:lintDebug :app-template:assembleDebug :app-template:lintDebug
./gradlew :compat-tests:verifyRoborazziDebug      # record: :compat-tests:recordRoborazziDebug
./scripts/check-docs.sh

# Server + debugging (endpoints: server-app/AGENTS.md)
./gradlew :server-app:run
curl localhost:8080/screens/main/debug            # decoded RC document tree

# Android end to end (emulator reaches the host at 10.0.2.2:8080)
./gradlew :app-template:installDebug && adb shell am start -n io.composecast.demo/.MainActivity
adb exec-out screencap -p > screen.png            # look at it
adb shell uiautomator dump /sdcard/u.xml && adb shell cat /sdcard/u.xml
```

## Current focus

README roadmap → **Etap 2 (Foundations)** and the iOS spike ([0007](docs/decisions/0007-ios-strategy-spike.md)).
