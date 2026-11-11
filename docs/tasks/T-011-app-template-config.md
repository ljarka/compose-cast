# T-011 — `app-template` configurable without code (`compose-cast.properties`)

Status: ready · Roadmap: Etap 7 · Depends on: —

## Goal
One file, `app-template/compose-cast.properties`, sets everything that differs between apps built
from the template: app name, `applicationId`, server URL, native frame colors, launcher icon. No
Kotlin/Gradle edits to make a new app — the weather app (Milestone 1) is the first user, the admin
export (T-010) writes this file.

## Why
Today `SERVER_URL` is a constant in `MainActivity`, `applicationId`/name are in Gradle/XML. Split out
of T-010 (2026-11-07 review): it does not need the admin panel and README Etap 7 already asked for a
"white-label shell".

## Note (2026-11-08, [ADR 0017](../decisions/0017-apps-in-own-repositories.md))
The configured app lives in **another repository** (first: weather app). Keep everything app-specific
in `compose-cast.properties` + icon so the template code never differs between apps; how the other
repository gets the template/SDK is decided ([ADR 0018](../decisions/0018-distribution-like-immich.md)):
T-024 moves this properties logic into the published Gradle plugin `io.composecast.app`, so write it
as a self-contained function/class (no `app-template`-specific paths) that can move unchanged.

## Design
```properties
app.name=ComposeCast Demo
app.applicationId=io.composecast.demo
app.serverUrl=http://10.0.2.2:8080
# native frame (top app bar, status bar, splash) before the first document arrives
app.frame.primaryColor=#6200EE
app.frame.backgroundColor=#FAFAFA
# optional PNG/JPG; empty = default icon
app.icon=
```
- `build.gradle.kts` reads the file: `applicationId`, `resValue("string", "app_name", …)`,
  `buildConfigField("String", "SERVER_URL", …)`, frame colors as `BuildConfig` fields; missing
  key → build error naming the key.
- **Release builds require `https://`** (cleartext is debug-only and local-only today): a release
  build with an `http` server URL fails with a clear message.
- Screen content colors are **not** configured here: buttons etc. are rendered by the server from
  its `BrandConfig` (`GET /brand`). Only the native frame shown around the documents is.
- `namespace` stays `io.composecast.demo` (code package); only `applicationId` changes.
- Icon: if set, generate adaptive-icon foreground PNGs per density with a small Gradle task
  (ImageIO scaling), else keep the default.

## Steps (files to touch)
1. `app-template/compose-cast.properties` (demo values), `app-template/build.gradle.kts`.
2. `MainActivity.kt`: `BuildConfig.SERVER_URL`, frame colors into `MaterialTheme`.
3. Icon task + default icon resources.
4. Screenshots unchanged (compat-tests do not use the frame colors from the app — check).

## Acceptance criteria
- Changing only the properties file → different app name, `applicationId` (installs side by side
  with the demo), server URL and frame colors on the emulator.
- `./gradlew :app-template:assembleRelease` fails for `http://` with a message naming
  `app.serverUrl`; passes for `https://`.
- `./scripts/verify.sh` green.

## Verification
`./scripts/verify.sh`; [verify-on-emulator.md](../playbooks/verify-on-emulator.md) with a second
properties file (install both apps).

## Docs to update
`app-template/AGENTS.md`, README (Running → "make your own app"), Etap 7 box, `docs/history.md`.

## Out of scope
Admin export (T-010), signing/Play upload, iOS.
