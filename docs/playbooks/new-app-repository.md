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
Pin `COMPOSECAST_VERSION` to the same release as the Android plugin version, and set
`JAVA_OPTS=-Xmx256m` on a small VPS (the heap otherwise grows to 75% of RAM).

- The server reads `data/` **at startup only**: `docker compose restart server` after editing a
  screen (until the admin panel publishes, T-007).
- Check a screen without the app: `curl localhost:8080/screens/<id>/debug` (texts after binding);
  validation errors stop startup with the file and JSON path (`docker compose logs server`).

## Android

1. Copy [`distribution/android-app/`](../../distribution/android-app) to `android/`.
2. `settings.gradle.kts` already points at compose-cast's repository `https://maven.jarka.club/releases`
   ([maven-repository.md](maven-repository.md)); change it only for a self-hosted mirror.
3. `build.gradle.kts`: plugin version = the compose-cast release (e.g. `0.1.0` for tag `v0.1.0`).
   Example of a whole app repository: the weather app (T-025).
4. `compose-cast.properties`: `app.name`, `app.applicationId` (unique, never change after
   publishing), `app.serverUrl` (https, required for release builds), frame colors, optional
   `app.icon`. Keys: [gradle-plugins/AGENTS.md](../../gradle-plugins/AGENTS.md).
   For development keep a second file, e.g. `compose-cast.local.properties`, with
   `app.serverUrl=http://10.0.2.2:8080` (the host from the emulator) and select it with
   `-Pcomposecast.appConfig=compose-cast.local.properties` — the release file stays untouched.
5. Gradle wrapper: copy `gradlew` and `gradle/wrapper/` from compose-cast (same Gradle 9.6.0), or
   run `gradle wrapper --gradle-version 9.6.0` with a local Gradle. `local.properties` with
   `sdk.dir=…` (gitignored) unless `ANDROID_HOME` is set. Then:

```bash
./gradlew -Pcomposecast.appConfig=compose-cast.local.properties installDebug   # emulator; activity io.composecast.shell.ComposeCastActivity
./gradlew assembleRelease               # https server URL; produces an unsigned APK (signing: T-044)
```

6. Optional: a Maestro flow that clicks through the screens. Maestro 2.x stores `takeScreenshot`
   files under its output directory: `maestro test --test-output-dir build/maestro <flow>.yaml`.

## Before a compose-cast release (or with unreleased framework changes)

Nothing in the app repository changes:

```bash
# in compose-cast: artifacts into build/maven-dry-run with the version the app asks for, server image
./gradlew -PcomposecastVersion=0.1.0 publish :gradle-plugins:publish
docker build -f server-app/Dockerfile -t ghcr.io/ljarka/compose-cast-server:local .
# in the app: COMPOSECAST_VERSION=local in server/.env; android/:
./gradlew --init-script <compose-cast>/scripts/local-maven.init.gradle.kts \
  -Pcomposecast.appConfig=compose-cast.local.properties installDebug
```

No Kotlin, manifest or resources in the app repository. Anything missing is a compose-cast feature
request, never app code (ADR 0017).

## Upgrade

Bump the plugin version in `build.gradle.kts` and `COMPOSECAST_VERSION` on the server together.

## Verified

2026-11-18 (T-025): the weather app ([ljarka/weather-app](https://github.com/ljarka/weather-app), 4 screens) built exactly this way —
`assembleDebug` and `assembleRelease` with no Kotlin, installed on the emulator against the local
image, Maestro flow through all screens passed.

`./scripts/verify-app-repository.sh` (part of `verify.sh`) builds exactly `distribution/android-app/`
from a publish dry run (`build/maven-dry-run`); `--install` puts it on the emulator. 2026-11-12:
installed side by side with the demo, Maestro `smoke.yaml` passed on both; publishing to a local
Reposilite 3.6.6 in Docker accepted all artifacts, anonymous read 200.
