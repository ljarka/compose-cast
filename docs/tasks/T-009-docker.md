# T-009 — Backend in one command (Docker)

Status: queued (after T-003) · Roadmap: Etap 6 · Depends on: T-003 (per-request rendering changes the runtime; package it once)

## Goal
`docker compose up` starts the server with screens from a mounted directory — no JDK, no Gradle,
no Kotlin for the user (README Vision principle 4, owner goal "backend easy to start").

## Why
Since the review after T-002 the server already serves JSON screens from `COMPOSECAST_DATA_DIR`
and reads `PORT`; only packaging is missing. Docker is installed on the dev machine
(`~/.docker/bin/docker`); CI (ubuntu-latest) has Docker too.

## Design
- `server-app/Dockerfile`, multi-stage: build with `eclipse-temurin:21-jdk` +
  `./gradlew :server-app:installDist`; run `eclipse-temurin:21-jre` with
  `server-app/build/install/server-app`. Pin image tags (no `latest`). Non-root user.
- `ENV COMPOSECAST_DATA_DIR=/data`, `VOLUME /data`, `EXPOSE 8080`,
  `HEALTHCHECK` → `GET /health` (the JRE image has no curl: use `wget` from a busybox stage or a
  tiny Java health client — decide and note it here).
- `docker-compose.yml` at the repo root: builds the image, mounts `./examples/demo:/data:ro`,
  maps `8080:8080`, `env_file: .env` (optional; data-source secrets come with T-006).
- Without `COMPOSECAST_DATA_DIR`, the image would serve the demo DSL screens; keep that default
  out of the image (always `/data`), so a missing mount fails loudly ("Screens directory … does not
  exist").
- Multi-arch (amd64 + arm64) via `docker buildx` is documented; publishing to GHCR is Etap 11.

## Steps (files to touch)
1. `server-app/Dockerfile`, `.dockerignore` (build/, .gradle/, local.properties, *.png goldens).
2. `docker-compose.yml`.
3. `scripts/verify.sh`: optional `--docker` flag (build image, run, curl `/health` and
   `/screens/main`), not part of the default run (keeps verify fast); CI job runs it.
4. README "Running" (docker first), `server-app/AGENTS.md`, `AGENTS.md` commands.

## Acceptance criteria
- `docker compose up -d && curl -s localhost:8080/screens` → `["actions","main","onboarding"]`.
- Editing a JSON file + `docker compose restart` shows the change in the app (emulator).
- Broken JSON → container exits with the file + JSON path in `docker compose logs`.
- Image < 250 MB; container healthy within 30 s.

## Verification
`./scripts/verify.sh` (+ `--docker`); emulator against the container
([verify-on-emulator.md](../playbooks/verify-on-emulator.md)).

## Docs to update
README (Running, roadmap Etap 6 box), `server-app/AGENTS.md`, `AGENTS.md` (commands),
`docs/history.md`.

## Out of scope
Publishing images (Etap 11), TLS / reverse proxy, admin panel (T-007).
