# 0018 — Distribution like Immich: release assets + GHCR image + Maven Central SDK

- Status: Accepted (owner, 2026-11-08)
- Date: 2026-11-08
- Resolves the open question of [ADR 0017](0017-apps-in-own-repositories.md): **option (a), published artifacts**

## Context

ADR 0017 puts every real app in its own repository with no framework code. The owner chose
option (a), publishing the Android pieces as libraries, and asked for a model "similar to
[Immich](https://immich.app/docs/install/docker-compose)". This is how Immich distributes its
self-hosted server:

- installation = download `docker-compose.yml` and `example.env` from the **latest GitHub Release**,
  rename to `.env`, set a few paths, `docker compose up -d`. No clone, no build;
- images on **GHCR**, multi-arch, tags `vX.Y.Z`, a moving `release` tag, and `main` for dev builds;
  the version is pinned by one variable in `.env` (`IMMICH_VERSION=release` or `v1.2.3`);
- update = `docker compose pull && docker compose up -d`; release notes flag breaking changes;
- the reverse proxy / TLS is the operator's choice; the docs show examples.

(Immich ships one generic mobile app where the user types the server URL. We ship **branded apps
instead**, one per app repository (ADR 0017), so we copy only the server side of Immich's model.
The Android pieces are published as libraries.)

## Decision

**Server: copy Immich's model.**
- Image `ghcr.io/ljarka/compose-cast-server`, `linux/amd64` + `linux/arm64`. Tags: `vX.Y.Z` and
  `release` on GitHub Releases, `main` on every push to `main`.
- Every GitHub Release has two assets, `docker-compose.yml` and `example.env` (sources in
  `distribution/`). The compose file uses `image: ghcr.io/ljarka/compose-cast-server:${COMPOSECAST_VERSION:-release}`
  and mounts `${COMPOSECAST_DATA_LOCATION:-./data}:/data`.
- HTTPS is built in but optional. A `caddy` service runs under the compose profile `https`
  (`COMPOSE_PROFILES=https`, `COMPOSECAST_DOMAIN=…`) and gets certificates automatically. We
  bundle it, unlike Immich, because release apps need `https://` (T-011).
- Install docs follow Immich: `mkdir`, two `wget`s from `releases/latest/download/`, edit `.env`,
  `docker compose up -d`. Update docs: `pull` + `up -d`.

**Android: published artifacts on Maven Central.**
- `protocol`, `sdk-android`, a new **`app-shell`** library (today's `app-template` `MainActivity`,
  navigation and error UI) and a **Gradle plugin `io.composecast.app`**. The plugin reads
  `compose-cast.properties` (T-011), sets `applicationId`, name, `BuildConfig`, the icon, and adds
  the `app-shell` dependency.
- An app repository's Android part: `settings.gradle.kts`, `build.gradle.kts` with
  `plugins { id("io.composecast.app") version "X.Y.Z" }`, `compose-cast.properties`, icon. No Kotlin.
- Maven Central rather than JitPack or GitHub Packages. GitHub Packages needs a token even to read
  public packages, so every app builder would hit that. JitPack builds on its own images, which lag
  behind AGP 9 / JDK 21 / compileSdk 37. Maven Central needs a one-time owner setup (below) but
  consumers need no extra repository.
- Server image and SDK share one version number. `app-shell` declares the RC document level it
  plays, so version negotiation (T-004) can rely on it.

**One release = one tag** `vX.Y.Z` → a workflow publishes the image, the Maven artifacts and the
release assets together.

## Consequences

- One-time owner setup: Maven Central account + namespace (`io.github.ljarka` is verified via
  GitHub login; a custom group like `io.composecast` needs a DNS TXT record on a domain the owner
  controls), a GPG signing key, and repository secrets. The GHCR package must be set public once.
- `app-template` in this repo becomes a consumer of the same plugin (via an included build), so the
  demo app proves the exact path that app repositories use.
- T-010 (admin export) becomes small: write `compose-cast.properties` + the four Gradle/config
  files of an app repository. No vendored sources.
- Breaking changes need release notes, like Immich's.

## How to verify it still holds

- `wget …/releases/latest/download/docker-compose.yml` + `example.env` + `docker compose up -d` on a
  clean machine serves `/health` (T-023 verification).
- An app repository builds with only Maven Central + Google repositories and has no `.kt` files.
