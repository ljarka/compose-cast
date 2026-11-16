# T-033 — AI assistant in the panel (optional, the user's own API key)

Status: queued (after T-037) · Roadmap: Etap 8 · Depends on: T-029, T-031 · Module: server-ai, server-admin

## Goal
The admin describes a screen in plain language ("a list of shows from the TVMaze API with
posters, tap opens the detail"). The assistant creates a **draft** screen in the canonical model,
and the user refines it in the visual editor. It can also restyle the brand and suggest a
`BrandConfig` palette.

## Why
Self-hosted fits "bring your own key": the key stays on the user's server. The strict model plus
`problems()` validation makes the generated output safe.

## Design
- Off by default. Enabled by `COMPOSECAST_AI_PROVIDER` + `COMPOSECAST_AI_API_KEY` (env/secret).
  Provider interface on the server, with at least one implementation; model ids are configurable,
  never hard-coded in many places.
- Prompt = the component schema from T-029 plus the probed JSON from T-031, if a source is chosen. The
  output must be `ScreenJson`. Validate with `problems()`, retry once with the errors, and else
  show them to the user.
- The output is always a draft and never auto-published; the diff is shown before saving.
- Privacy: only what the user typed, the schema and optionally a truncated API sample are sent.
  The panel says so. No screen data is sent without an explicit action.

## Acceptance criteria
- With a key configured: 5 fixed prompts produce valid drafts (an integration test, skipped without
  a key). Without a key, the feature is hidden.
- A unit test with a fake provider covers validation and the retry.

## Docs to update
README (Etap 8, includes the former "AI brand setup"), install playbook (env vars), new ADR
(provider and privacy), history.
