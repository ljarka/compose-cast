# T-023 — App repositories: published server image + app shell for other repos

Status: needs design detail (owner decision on SDK distribution, ADR 0017) · Roadmap: Etap 6 · Depends on: T-009, T-011

## Goal
A new repository (first: the weather app) can, with **no compose-cast code changes and no copied
framework code**: deploy the server on a VPS from a published image, and build a branded Android
app from configuration only ([ADR 0017](../decisions/0017-apps-in-own-repositories.md)).

## Why
Owner, 2026-11-08: the weather app uses the framework as-is; the backend is installed on a VPS,
the app is adjusted by configuration, and the adjusted app lives in its own repository.

## Design (to finish before `ready`)
1. **Server image on GHCR**: workflow `.github/workflows/image.yml` — on tags `v*` (and `main` as
   `edge`): `docker/setup-buildx-action`, `docker/build-push-action` with
   `platforms: linux/amd64,linux/arm64`, `file: server-app/Dockerfile`, image
   `ghcr.io/ljarka/compose-cast-server`. The owner must make the package public once (GitHub UI).
   T-009 verified the multi-arch build locally (build stage on `$BUILDPLATFORM`).
2. **Android shell + SDK** — owner decides (a) published artifacts or (b) standalone export
   (ADR 0017, recommendation (a)). For (a): `maven-publish` for `protocol`, `sdk-android`, and a new
   library module with `app-template`'s `MainActivity`/navigation so the app repo has no Kotlin.
3. **App repository template** (documented here, created by the owner): `server/screens/*.json`,
   `server/brand.json`, `docker-compose.yml` (image tag pinned, volume `./server:/data:ro`,
   `.env`, Caddy for HTTPS with automatic certificates), `app/` (Gradle + `compose-cast.properties`).
4. A playbook `docs/playbooks/new-app-repository.md`: create repo, deploy to VPS, build the app.

## Acceptance criteria
- `docker compose up -d` on a fresh VPS (only Docker installed) serves the app repo's screens over
  HTTPS without cloning compose-cast.
- The app repository builds a release APK pointing at that HTTPS server; no Kotlin sources in it
  (option a) or byte-identical template sources (option b).

## Verification
Fresh clone of the app repository on a machine without compose-cast; emulator against the VPS.

## Docs to update
README (Etap 6/9/11 boxes), `docs/playbooks/`, `app-template/AGENTS.md`, `server-app/AGENTS.md`,
`docs/history.md`.

## Out of scope
Admin panel export (T-010), Play Store upload, iOS.
