# Playbook — install the compose-cast server (like Immich)

For people deploying an app's backend on a VPS or any machine with Docker
([ADR 0018](../decisions/0018-distribution-like-immich.md), T-023). No clone, no JDK, no build:
the server is the published image `ghcr.io/ljarka/compose-cast-server`.

Prerequisites: Docker with Compose v2 (`docker compose version`). For HTTPS: a domain whose DNS
A/AAAA record points to the machine, ports 80 and 443 open.

## Install

```bash
mkdir my-app && cd my-app
wget -O docker-compose.yml https://github.com/ljarka/compose-cast/releases/latest/download/docker-compose.yml
wget -O .env https://github.com/ljarka/compose-cast/releases/latest/download/example.env
mkdir -p data/screens            # your app: data/screens/<id>.json, optional data/brand.json
nano .env                         # see "Settings" below
docker compose up -d
curl localhost:8080/screens       # JSON list of your screen ids
```

A first screen to start from: [`examples/demo`](../../examples/demo) in this repository (copy it
to `./data`). A missing or invalid screen file stops the server; `docker compose logs server`
names the file and the JSON path.

## Settings (`.env`)

| Variable | Default | Meaning |
|---|---|---|
| `COMPOSECAST_VERSION` | `release` | Image tag: `release` (latest release), a version such as `v0.1.0` (pin), or `main` (development builds) |
| `COMPOSECAST_DATA_LOCATION` | `./data` | The app's data directory, mounted read-only at `/data` |
| `COMPOSECAST_PORT` | `8080` | Host port; with HTTPS use `127.0.0.1:8080` so only Caddy is public |
| `COMPOSE_PROFILES` | — | `https` starts Caddy (reverse proxy, automatic Let's Encrypt certificates) |
| `COMPOSECAST_DOMAIN` | `localhost` | Domain Caddy serves and obtains the certificate for |
| `COMPOSECAST_SECRET_<NAME>` | — | Secret for data sources, read by `{{secret.NAME}}` in a data source URL/header (never sent to the app) |

The whole `.env` is passed to the server container, so secrets only need to be added there.

## HTTPS

```bash
# in .env
COMPOSE_PROFILES=https
COMPOSECAST_DOMAIN=app.example.com
COMPOSECAST_PORT=127.0.0.1:8080
```

`docker compose up -d`, then `curl https://app.example.com/screens`. Caddy redirects http → https
and renews certificates itself (stored in the `caddy-data` volume — keep it, Let's Encrypt
rate-limits re-issuing). Release Android builds require an `https://` server URL (T-011).
Verified locally with `COMPOSECAST_DOMAIN=localhost` (Caddy's internal CA, `curl -k`): 2026-11-12.

## Edit screens, update, back up

```bash
docker compose restart server                       # after editing files in ./data (read at start)
docker compose pull && docker compose up -d         # update to the newest image of COMPOSECAST_VERSION
docker image prune                                  # remove old images afterwards
```

Back up the data directory (and `.env`, which holds secrets) — the server stores nothing else.
Several apps on one machine: one directory per app (the directory name is the Compose project), each
with its own `COMPOSECAST_PORT`; with HTTPS, put one Caddy in front of all of them instead of the
`https` profile (ports 80/443 can only be taken once).

## For maintainers

- The files above are `distribution/docker-compose.yml` and `distribution/example.env`; the root
  `docker-compose.yml` is the dev setup that builds from source.
- `.github/workflows/release.yml`: push to `main` → image `:main`; a published GitHub Release
  (tag `vX.Y.Z`) → `:vX.Y.Z` + `:release` and the two files as release assets. Do **not** mark a
  release as pre-release: `releases/latest/download/…` skips pre-releases and the install command
  would 404.
- `./scripts/verify.sh --docker` installs `distribution/docker-compose.yml` into an empty directory
  against the locally built image and validates the `https` profile.
- One-time (owner): after the first run of the workflow, GitHub → Packages →
  `compose-cast-server` → Package settings → Change visibility → Public (otherwise pulls need a login).
