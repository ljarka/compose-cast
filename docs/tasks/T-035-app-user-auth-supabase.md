# T-035 — App user accounts via Supabase (login, "my" data)

Status: queued (after T-034) · Roadmap: Etap 10 · Depends on: T-034 (forms), T-007 · Module: server-auth, sdk-android, app-shell

## Goal
End users of an app can sign in (email one-time code, later OAuth), and screens can show
per-user data ("my orders", "profile") stored in the owner's **Supabase** project with Row Level
Security.

## Why
Owner choice (2026-11-16): Supabase as the auth/data backend. Each compose-cast server connects
to *its own* Supabase project, which fits self-hosting and no multi-tenancy.

## Design (verify the current Supabase Auth/REST API before implementing; never guess)
- Configuration in the admin App Settings: Supabase URL plus the anon key (public) and an optional
  service key (secret, server only, never sent to the app).
- Login flow through **built-in screens** (`login`, `verify-code`) rendered like any screen: submit
  (T-034) → the server calls Supabase Auth (email OTP) → it returns the session (access + refresh
  token) to the app → the shell stores it in encrypted storage (Android Keystore) and sends
  `Authorization: Bearer` with screen requests.
- The server verifies the JWT (Supabase JWKS/secret) and exposes `{{user.id}}`, `{{user.email}}`,
  `{{user.token}}` to data-source templates, so a source can call Supabase REST **with the user's
  token**, and RLS enforces access. `{{user.token}}` is allowed only in source headers (like
  secrets) and never in components.
- Screen field `requiresAuth: true` → unauthenticated users are redirected to `login`. Action
  `logout`.
- Responses for authenticated screens get `Cache-Control: private, no-store` (affects T-028).
- Generic design: an `AuthProvider` interface so another provider can be added later, with Supabase
  as the first implementation.

## Acceptance criteria
- Against a test Supabase project (or a local `supabase start`): log in on the emulator, see a
  per-user list protected by RLS, log out. Integration tests are skipped without configuration.
- Tests: an invalid or expired JWT → login; a token never appears in `/debug` output or logs.

## Docs to update
New ADR (auth model, token handling), playbook "connect Supabase", `sdk-android/AGENTS.md`,
README, history.
