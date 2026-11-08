# 0019 — Android artifacts on a self-hosted Maven repository (Reposilite on the VPS)

- Status: Accepted (owner, 2026-11-08)
- Date: 2026-11-08
- Supersedes the **Maven Central** part of [ADR 0018](0018-distribution-like-immich.md). The rest
  of 0018 (Immich-style server, GHCR image, artifacts, plugin, no Kotlin in app repos) stays.

## Context

ADR 0018 chose Maven Central for `protocol`, `sdk-android`, `app-shell` and the
`io.composecast.app` Gradle plugin. The owner prefers to host the artifacts themselves, on their VPS,
exposed through Cloudflare. Mobile apps stay **branded and in separate repositories**: Immich is the
model for the backend only (owner, 2026-11-08).

Options compared (2026-11-08):
- **Reposilite** (Apache 2.0, https://reposilite.com): single Java app for Maven repositories,
  Docker image `dzikoysk/reposilite` (3.6.6), data in `/app/data`, ~100 MB RAM, token auth with
  per-path read/write routes, web UI. Fits 4 artifacts.
- Sonatype Nexus Community: works, 2–4 GB RAM. JFrog Artifactory OSS: heavy, limited free edition.
  Apache Archiva: retired. Gitea/Forgejo packages: a whole Git server for this.

## Decision

- **Reposilite 3.6.x on the owner's VPS**, in its own `docker-compose.yml` (playbook
  [maven-repository.md](../playbooks/maven-repository.md)), public URL via **Cloudflare Tunnel**
  (`cloudflared`, no inbound ports), e.g. `https://maven.<domain>/releases`.
- Repository `releases`: **anonymous read**, write only with the token `ci`
  (`route-add ci /releases rw`); its secret is the GitHub Actions secrets
  `COMPOSECAST_MAVEN_USERNAME` (`ci`) / `COMPOSECAST_MAVEN_PASSWORD`.
- Group `io.composecast` (we own the namespace on our own repository; no domain proof needed).
  Artifacts: `io.composecast:protocol`, `io.composecast:sdk-android`, `io.composecast:app-shell`,
  plugin id `io.composecast.app` (+ its marker `io.composecast.app:io.composecast.app.gradle.plugin`).
- Publishing: plain `maven-publish` (no signing required), repository name `composecast`, URL from
  the Gradle property `composecastMavenUrl`; the release workflow (T-023/T-024) publishes on tags.
  Released versions are never overwritten (Reposilite setting `redeployment` stays off).
- App repositories add the repository in `settings.gradle.kts` for both plugins and dependencies.

## Consequences

- No Maven Central account, namespace proof or GPG key is needed. **The owner runs one more service**:
  if the VPS is down, fresh clones and app-repo CI cannot resolve artifacts (cached builds still
  work). Back up the `reposilite-data` volume like the backend data.
- Cloudflare Access (if used) must not protect `/releases` reads, otherwise Gradle gets a login page.
- App repositories need one extra repository line; the plugin is not on the Gradle Plugin Portal.
- Moving to Maven Central later is additive (same coordinates if the group is verifiable there;
  otherwise a new group), so this choice is reversible.

## How to verify it still holds

`curl -fsS https://maven.<domain>/releases/io/composecast/sdk-android/maven-metadata.xml` lists the
released versions; an app repository resolves the plugin with only Google, Maven Central, the
Gradle Plugin Portal and this repository configured.
