# Playbook — a new app repository (server + Android, no Kotlin)

How a product app built with compose-cast lives in its own repository
([ADR 0017](../decisions/0017-apps-in-own-repositories.md), [ADR 0018](../decisions/0018-distribution-like-immich.md)).
The repository holds only configuration and content; compose-cast is pulled as a published server
image and published Android artifacts. First user: the example weather app (Milestone 1).

```
my-app/
├── server/                      # deployed on the VPS (install-server.md)
│   ├── docker-compose.yml       # from the compose-cast GitHub Release
│   ├── example.env              # committed template; the real .env (secrets) is NOT committed
│   └── data/
│       ├── brand.json
│       └── screens/<id>.json
└── android/                     # copy of distribution/android-app/
    ├── settings.gradle.kts      # Maven repository URL (twice)
    ├── build.gradle.kts         # plugins { id("io.composecast.app") version "<x.y.z>" }
    ├── gradle.properties
    ├── compose-cast.properties  # name, applicationId, server URL, frame colors, icon
    ├── icon.png                 # optional
    └── gradle/wrapper/…, gradlew # `gradle wrapper --gradle-version 9.6.0` once
```

## Server

Follow [install-server.md](install-server.md) in `server/`, with `COMPOSECAST_DATA_LOCATION=./data`.
Pin `COMPOSECAST_VERSION` to the same release as the Android plugin version.

## Android

1. Copy [`distribution/android-app/`](../../distribution/android-app) to `android/`.
2. `settings.gradle.kts` already points at compose-cast's repository `https://maven.jarka.club/releases`
   ([maven-repository.md](maven-repository.md)); change it only for a self-hosted mirror.
3. `build.gradle.kts`: plugin version = the compose-cast release (e.g. `0.1.0` for tag `v0.1.0`).
4. `compose-cast.properties`: `app.name`, `app.applicationId` (unique, never change after
   publishing), `app.serverUrl` (https for release; `http://10.0.2.2:8080` while developing against
   a local server), frame colors, optional `app.icon`. Keys: [gradle-plugins/AGENTS.md](../../gradle-plugins/AGENTS.md).
5. `gradle wrapper --gradle-version 9.6.0` (needs a local Gradle once), then:

```bash
./gradlew installDebug                  # emulator/device; activity io.composecast.shell.ComposeCastActivity
./gradlew assembleRelease               # needs app.serverUrl=https://…; sign it for a store (not covered here)
```

No Kotlin, manifest or resources in the app repository. Anything missing is a compose-cast feature
request, never app code (ADR 0017).

## Upgrade

Bump the plugin version in `build.gradle.kts` and `COMPOSECAST_VERSION` on the server together.

## Verified

`./scripts/verify-app-repository.sh` (part of `verify.sh`) builds exactly `distribution/android-app/`
from a publish dry run (`build/maven-dry-run`); `--install` puts it on the emulator. 2026-11-12:
installed side by side with the demo, Maestro `smoke.yaml` passed on both; publishing to a local
Reposilite 3.6.6 in Docker accepted all artifacts, anonymous read 200.
