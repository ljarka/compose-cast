# T-026 — Image fetch hardening (no requests to private addresses)

Status: ready · Roadmap: Etap 6 · Depends on: —

## Goal

The server's `HttpImageLoader` must never fetch from loopback, private or link-local addresses,
including after a redirect. This closes an SSRF hole before screens can be edited in the admin
panel (T-007).

## Why

- Image URLs are bound *without* URL encoding (`Binder.kt`, `image`/`icon` branches). A screen with
  `"url": "{{params.img}}"` therefore lets any app user make the server fetch
  `http://127.0.0.1:…`, `http://169.254.169.254/…` (cloud metadata) or LAN hosts.
- Image URLs taken from external API responses (e.g. TVMaze posters) are fetched too.
- Already in place: `http`/`https` only, 5 MB cap, timeouts (`ImageLoader.kt`).
- Data-source URLs encode parameters (`Binder.kt:133`), so a client cannot change their host. They
  are author-controlled and out of scope here.

## Design

- Before connecting, resolve the host (`InetAddress.getAllByName`) and reject the URL if **any**
  resolved address is loopback, site-local, link-local, any-local, multicast, or IPv6 ULA
  `fc00::/7`.
- Redirects: switch to `Redirect.NEVER` and follow at most 3 redirects by hand, checking every hop.
- Opt-out for local development: `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS=true` (documented, default
  `false`). Check whether the demo or `compat-tests` use local images: tests inject a fake
  `ImageLoader`, so they should not need it.
- A rejected URL behaves like a failed fetch: alt text plus one warning (ADR 0003).
- DNS rebinding (resolve, then connect to another IP) is accepted as residual risk. Write it down
  in the KDoc.

## Steps (files to touch)

1. `sdk-server/.../ImageLoader.kt`: address check, manual redirects, env opt-out (read in
   `server-app` and passed in; `sdk-server` does not read env).
2. `sdk-server` tests: a pure address-classification function (`127.0.0.1`, `10.x`, `192.168.x`,
   `169.254.169.254`, `::1`, `fd00::1` rejected; a public IP allowed) and redirect-hop rejection
   against a local Ktor test server.
3. `server-app`: wire the opt-out.

## Acceptance criteria

- The unit tests above are green; an image pointing at `http://127.0.0.1:8080/health` renders alt
  text and logs a warning.
- `./scripts/verify.sh` is green, with screenshots unchanged.

## Docs to update

ADR 0003 (consequence: private hosts blocked), `sdk-server/AGENTS.md`, `server-app/AGENTS.md`
(env var), `docs/playbooks/install-server.md` env table, `docs/history.md`.

## Out of scope

Restricting data-source hosts (author-controlled; revisit with admin roles in T-007).
