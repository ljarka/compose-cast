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
| `COMPOSECAST_DATA_LOCATION` | `./data` | The app's data directory, mounted at `/data` (files mode only reads it; admin mode keeps `compose-cast.db` there) |
| `COMPOSECAST_MODE` | `files` | `admin`: the admin panel at `/admin` and a database instead of files (see "Admin panel" below) |
| `COMPOSECAST_ADMIN_EMAIL`, `COMPOSECAST_ADMIN_PASSWORD` | — | Admin mode: the first admin account (min. 12 characters), required at the first start, ignored once an account exists |
| `COMPOSECAST_ADMIN_ALLOW_HTTP` | `false` | `true`: the login cookie is not `Secure`, so the admin works over plain http (testing only; production uses HTTPS) |
| `COMPOSECAST_PORT` | `8080` | Host port; with HTTPS use `127.0.0.1:8080` so only Caddy is public |
| `COMPOSE_PROFILES` | — | `https` starts Caddy (reverse proxy, automatic Let's Encrypt certificates) |
| `COMPOSECAST_DOMAIN` | `localhost` | Domain Caddy serves and obtains the certificate for |
| `JAVA_OPTS` | — (image: `-XX:MaxRAMPercentage=75`) | JVM options. On a small VPS cap the heap, e.g. `-Xmx256m`: by default the heap grows to 75% of RAM under load (T-025 measurements in the README) |
| `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS` | `false` | `true` lets screens load images from loopback/private/link-local hosts (local development only; keep `false` in production — SSRF) |
| `COMPOSECAST_RELOAD` | `true` | Files mode: reload `data/` when its files change (polled every 2 s; an invalid edit is logged and the previous screens stay served). `false`: read once at startup |
| `COMPOSECAST_SECRET_<NAME>` | — | Secret for data sources, read by `{{secret.NAME}}` in a data source URL/header (never sent to the app) |

The whole `.env` is passed to the server container, so secrets only need to be added there.

## Admin panel (T-007)

Instead of editing files, edit screens in the browser: list, JSON editor with validation, drafts,
publish without restart, history with rollback, brand colors and app settings.

```bash
# in .env (HTTPS first: the login only works over https:// or on localhost)
COMPOSECAST_MODE=admin
COMPOSECAST_ADMIN_EMAIL=you@example.com
COMPOSECAST_ADMIN_PASSWORD=a-long-password-12+
```

```bash
sudo chown -R 1000:1000 data        # the server (UID 1000) writes data/compose-cast.db
docker compose up -d
docker compose logs server | grep -i admin   # "Created the first admin account …"
```

Then open `https://<COMPOSECAST_DOMAIN>/admin`. The first start imports `data/screens/*.json` and
`data/brand.json` into the database once; afterwards the database is the source of truth and the
files are ignored (edit in the panel). Roles: `admin` (everything), `editor` (screens), `viewer`
(read only); more accounts: not in the UI yet. Without the chown the server stops with a message
saying so; without the two `COMPOSECAST_ADMIN_*` variables on the first start as well.

Back up `data/compose-cast.db` (with `docker compose stop` first, or `sqlite3 data/compose-cast.db
".backup backup.db"` while running): it holds the screens, their history, the settings and the
accounts.

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
docker compose logs -f server                       # edits in ./data are served within ~3 s ("Reloaded …"; errors here)
docker compose pull && docker compose up -d         # update to the newest image of COMPOSECAST_VERSION
docker image prune                                  # remove old images afterwards
```

Back up the data directory (and `.env`, which holds secrets) — the server stores nothing else (in
admin mode the data directory includes `compose-cast.db`).
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
  against the locally built image and validates the `https` profile; it also starts admin mode on a
  named volume (env admin logs in) and checks that a read-only `/data` is refused.
- Visibility: the package `compose-cast-server` inherits the public repository's visibility (no
  setting needed; anonymous pull verified 2026-11-12). Check with
  `curl -s "https://ghcr.io/token?scope=repository:ljarka/compose-cast-server:pull"` + the manifest
  URL, or `docker logout ghcr.io && docker pull ghcr.io/ljarka/compose-cast-server:main`.
