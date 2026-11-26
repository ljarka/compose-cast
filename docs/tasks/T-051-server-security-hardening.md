# T-051 — Server security hardening (SSRF in data loader, login rate limit, 500 error sanitization, CSP)

Status: done · Roadmap: quality · Depends on: T-050 · Module: sdk-server, server-admin, server-app

## Goal

Close the remaining SSRF gap in `HttpDataLoader`, protect `/admin/login` against brute-force and
BCrypt CPU exhaustion, stop leaking raw internal exception messages in HTTP 500 responses, and add a
`Content-Security-Policy` header to admin pages.

## Why

- `T-026` explicitly deferred restricting data-source hosts until admin roles existed (`T-007`).
  Now editors can author data source URLs in the admin panel: `ApiProbe` and `HttpImageLoader`
  block private/loopback/link-local addresses and check every redirect hop, while `HttpDataLoader`
  still uses `Redirect.NORMAL` with no address check.
- `POST /admin/login` runs BCrypt (cost 12) directly on Ktor request threads with no rate limit.
- `StatusPages` in `server-app/Main.kt` returns `cause.message` on unhandled `Throwable` (HTTP 500).
- `SecurityHeaders` in `server-admin` lacks `Content-Security-Policy`.

## Design

- Extract shared private-address & manual-redirect validation in `sdk-server` (used by both
  `HttpImageLoader` and `HttpDataLoader`, with `allowPrivateHosts: Boolean = false` wired from
  `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS` / `COMPOSECAST_ALLOW_PRIVATE_HOSTS`).
- Add an in-memory per-IP rate limiter for `POST /admin/login` (e.g. 10 failed attempts per
  5-minute window → HTTP 429) and run `auth.login(...)` inside `withContext(Dispatchers.Default)`.
- Return a generic `"Internal server error"` message for unhandled `Throwable` in `StatusPages`
  while keeping full exception logging server-side.
- Add a `Content-Security-Policy` header to `SecurityHeaders` compatible with the admin templates
  and inline HTMX/editor scripts.

## Steps (files to touch)

1. `sdk-server/.../DataLoader.kt` and `ImageLoader.kt`: share private-host & redirect checks.
2. `server-app/.../Main.kt` and `AdminScreens.kt`: pass `allowPrivateHosts` to `HttpDataLoader`;
   sanitize 500 response body.
3. `server-admin/.../AdminPanel.kt` (+ small `LoginRateLimiter.kt`): rate limit `POST /admin/login`,
   offload BCrypt to `Dispatchers.Default`, add `Content-Security-Policy`.
4. Unit tests in `sdk-server`, `server-admin`, and `server-app`.

## Acceptance criteria

- `HttpDataLoader` rejects private/loopback/link-local hosts and private redirect hops by default.
- Repeated failed logins from one IP return HTTP 429; other IPs are unaffected; successful login
  clears the failure counter.
- Unhandled exceptions return HTTP 500 with `"Internal server error"` (no stack/message leak).
- Admin pages include `Content-Security-Policy`.
- `./scripts/verify.sh` is green.

## Verification

`./scripts/verify.sh`

## Docs to update

`sdk-server/AGENTS.md`, `server-admin/AGENTS.md`, `server-app/AGENTS.md`, `docs/history.md`.

## Out of scope

Distributed (multi-node) rate limiting; `compose-cast` server is single-node SQLite.
