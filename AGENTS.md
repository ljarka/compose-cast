# compose-cast — Project AGENTS.md

Server-driven UI framework for Android built on Remote Compose (AndroidX).

## Module Map

| Module | Scope | Status |
|---|---|---|
| `protocol` | Shared serializable types (ScreenModel, ComponentModel, BrandConfig, Actions) | done |
| `sdk-server` | Kotlin DSL for building screens; ScreenRegistry | done |
| `server-scripting` | `.screen.kts` script host (hot-reload) | empty |
| `server-app` | Ktor HTTP server; uses sdk-server DSL | done |
| `sdk-android` | Android SDK: fetch + render screens | empty |
| `app-template` | Demo Android app | empty |
| `compat-tests` | Golden-file compatibility tests | empty |

## Task Strategy

Each task targets exactly one module. Load only the relevant module's `AGENTS.md` + its direct deps.

Never load the whole project. Load root `AGENTS.md` only for cross-module decisions.

## Run Server

```bash
cd /root/compose-cast
./gradlew :server-app:run
```

Endpoints:
- `GET /health` → `ok`
- `GET /brand` → BrandConfig JSON
- `GET /screens` → list of screen IDs
- `GET /screens/{id}` → ScreenModel JSON

## Key Decisions

- Protocol types use `kotlinx.serialization`; sealed class `ComponentModel` with `@SerialName` discriminators
- DSL is in `sdk-server`; `server-app` only wires Ktor + calls DSL
- `server-scripting` will use Kotlin Script Host for `.screen.kts` hot-reload (not started)
- `sdk-android` will use Retrofit + Remote Compose `RemoteComposePlayer` (not started)

## Git

```bash
cd /root/compose-cast
git add -A && git commit -m "..."
git push
```
