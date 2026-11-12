# T-023 — Server distribution like Immich (GHCR image + release assets)

Status: done (owner actions below still open) · Roadmap: Etap 6 · Depends on: T-009 (image)

## Goal
An app repository (first: the weather app) runs its backend on a VPS the way Immich users install
Immich, without cloning or building compose-cast
([ADR 0017](../decisions/0017-apps-in-own-repositories.md), [ADR 0018](../decisions/0018-distribution-like-immich.md)):

```bash
mkdir my-app && cd my-app
wget -O docker-compose.yml https://github.com/ljarka/compose-cast/releases/latest/download/docker-compose.yml
wget -O .env https://github.com/ljarka/compose-cast/releases/latest/download/example.env
# put screens/<id>.json + brand.json into ./data, set COMPOSECAST_DOMAIN in .env
docker compose up -d
```
The Android half (artifacts on Reposilite + Gradle plugin) is T-024.

## Design
1. `distribution/docker-compose.yml` (production shape; the root `docker-compose.yml` stays the
   dev setup that builds from source):
   - `server`: `image: ghcr.io/ljarka/compose-cast-server:${COMPOSECAST_VERSION:-release}`,
     `volumes: ["${COMPOSECAST_DATA_LOCATION:-./data}:/data:ro"]`, `env_file: .env`,
     `restart: always`, healthcheck inherited from the image,
     `ports: ["${COMPOSECAST_PORT:-8080}:8080"]`. A profile cannot switch ports, so HTTPS setups set
     `COMPOSECAST_PORT=127.0.0.1:8080` (only Caddy is public); document it in `example.env`.
   - `caddy`: `caddy:2.x` (pinned), profile `https`, ports 80/443, `command: caddy reverse-proxy
     --from ${COMPOSECAST_DOMAIN} --to server:8080`, volume `caddy-data` for certificates.
2. `distribution/example.env`: `COMPOSECAST_VERSION=release`, `COMPOSECAST_DATA_LOCATION=./data`,
   `COMPOSECAST_PORT=8080`, `# COMPOSE_PROFILES=https`, `# COMPOSECAST_DOMAIN=app.example.com`,
   comment block for data-source secrets (T-006), all commented like Immich's.
3. `.github/workflows/release.yml`:
   - on `push` to `main`: buildx multi-arch, push `ghcr.io/ljarka/compose-cast-server:main`
     (`permissions: packages: write`, `GITHUB_TOKEN`);
   - on `release: published` (tag `vX.Y.Z`): push `vX.Y.Z` + `release`, upload
     `distribution/docker-compose.yml` and `distribution/example.env` as release assets
     (`gh release upload` with `GITHUB_TOKEN`). T-024 adds the Maven publish to the same workflow.
   - Image labels: `org.opencontainers.image.source=https://github.com/ljarka/compose-cast` (links
     the package to the repo), version, revision.
4. `scripts/verify-docker.sh`: also start `distribution/docker-compose.yml` against the locally
   built image (`COMPOSECAST_VERSION` override via retag) to keep the release compose file working.
5. Docs: `docs/playbooks/install-server.md` (Immich-style install, update = `docker compose pull &&
   docker compose up -d`, backups = the data directory, HTTPS profile, DNS A record prerequisite),
   linked from README Quick start.

## Owner actions (cannot be done by an agent)
- After the first `main` push of the workflow: GitHub → Packages → `compose-cast-server` → make public.
- Create the first GitHub Release (`v0.1.0-alpha01` or similar) to produce `release` + assets.
  **Not marked as pre-release**: `releases/latest/download/…` ignores pre-releases (install would 404).

## Acceptance criteria
- On a clean machine/VPS with only Docker: the commands in Goal serve `/health`, and with
  `COMPOSE_PROFILES=https` + a domain, `https://<domain>/screens` works with a valid certificate.
- `docker compose pull && docker compose up -d` upgrades to a newer `release`.
- `./scripts/verify.sh --docker` checks both compose files.

## Verification
`./scripts/verify.sh --docker`; a real VPS (owner) for HTTPS; emulator against the HTTPS server with
a release build after T-024.

## Docs to update
README (Quick start → install playbook; Etap 6/9/11 boxes), `server-app/AGENTS.md`,
`docs/playbooks/README.md`, `docs/history.md`.

## Out of scope
Android artifacts (T-024), admin panel (T-007; it will need a writable `/data`), backups automation.
