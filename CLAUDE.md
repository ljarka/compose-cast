# CLAUDE.md — compose-cast

> This file is read by Claude Code at session start. `AGENTS.md` (same directory) is the
> authoritative index for all other AI agents. Both files must stay in sync.

## Start here

1. Read `AGENTS.md` (this directory) — module map, rules, Definition of Done.
2. Run `./scripts/verify.sh --quick` before touching anything; it must be green.
3. Without a specific owner request, take the first `ready` task in `docs/tasks/README.md`.
4. Before touching a module, read its own `AGENTS.md`.

## Critical rules (enforced by `scripts/check-architecture.sh`)

| # | Rule |
|---|---|
| R1 | RC creation API only in `sdk-server/…/RemoteComposeRenderer.kt` |
| R2 | RC player API only in `sdk-android` |
| R3 | No RC types in public APIs or `protocol` |
| R4 | Never apply `org.jetbrains.kotlin.android` plugin (AGP 9 built-in Kotlin) |
| R5 | Compose always via BOM (`platform(libs.compose.bom)`) |
| R6 | All versions pinned in `gradle/libs.versions.toml` |
| R7 | `server-scripting` must not depend on Ktor |

Breaking any rule → `./scripts/verify.sh` fails. Fix the code, not the script.

## Never guess RC behavior

Read the sources jar instead:

```bash
V=1.0.0-alpha23; A=remote-creation-core
curl -sfLO https://dl.google.com/android/maven2/androidx/compose/remote/$A/$V/$A-$V-sources.jar
unzip -q $A-$V-sources.jar -d $A
```

Record findings in `docs/remote-compose.md` with the version.

## Wire format

The Android wire format is **binary Remote Compose (`.rc`)** — not JSON, not protobuf, not custom.
See [ADR 0001](docs/decisions/0001-remote-compose-wire-format.md).

## Verification

```bash
./scripts/verify.sh          # full (needs Android SDK)
./scripts/verify.sh --quick  # JVM only (no Android SDK needed)
```

CI runs `./scripts/verify.sh` (full). Green locally = green in CI.

## Environment

- JDK 21. In agent shells without `~/.zshrc`: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`
- Android SDK: `~/Library/Android/sdk` (mac) or `$ANDROID_HOME` (CI)
- Port: `8080`

## Full documentation index

See `AGENTS.md` → "Where knowledge lives" table.
