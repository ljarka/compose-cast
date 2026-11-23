# T-031 — Data source wizard (URL → ready list/detail screens)

Status: in progress — next step: W2 (W1 done) · Roadmap: Etap 8 · Depends on: T-006, T-029 · Module: server-admin, sdk-server

## Goal
A non-developer pastes an API URL. The panel fetches it, shows the JSON as a tree and asks "what
is the list?". It then generates a **list screen** and a **detail screen** (navigate with an id
param) with sensible bindings. All of it is editable afterwards in the visual editor.

## Why
`{{source.path}}` bindings are the hardest part for non-developers. ADR 0008 keeps API keys on
the server, so the wizard also runs on the server.

## Design
- Endpoint `POST /admin/api/sources/probe {url, headers}` → status, content type, truncated JSON
  (max 256 KB), response time. Uses the same SSRF rules as T-026 (private hosts blocked) and is
  limited to the `editor`/`admin` roles.
- Heuristics: find arrays of objects; per field, guess its role (title = first short string;
  image = a URL ending in an image extension or a field named `image|thumbnail|poster`; id =
  `id|slug`; date = ISO date) → a `repeat` with a card template.
- Detail: a second source with a URL template (user picks the id param, e.g. `/shows/{{params.id}}`)
  or reuse the list item if the API has no detail endpoint.
- Field picker in the T-029 property forms: clicking a JSON node inserts the binding path.
- Secrets: a header value can reference `{{secret.NAME}}`; the wizard never stores raw keys in
  screen JSON (it offers "save as secret" → instructions for `.env`, or the T-007 settings store
  if one exists).

## Steps

1. **W1** Probe + heuristics: `ApiProbe` (server-side GET, public hosts only — every redirect hop
   checked —, 256 KB, `{{secret.NAME}}` from env), `ApiShape` (lists of objects, fields with
   kinds, roles id/title/subtitle/image), filter `plain` (API HTML → text). Done when: heuristics
   tested on 3 fixture APIs, the probe refuses private addresses.
   **W1 result (2026-11-23):** done — `ApiShapeTest` (TVMaze shows, Rick and Morty characters,
   JSONPlaceholder posts; trimmed copies in `server-admin/src/test/resources/fixtures/apis/`),
   `ApiProbeTest` (private/loopback/link-local refused before any request, also after a redirect),
   `DataBindingTest` (`plain`).
2. **W2** Wizard page `/admin/wizard`: URL (+ headers) → probe → pick the list and the fields
   (pre-filled with the guesses) → generates a list screen (`repeat` of cards, tap → detail) and a
   detail screen (a detail URL template such as `…/shows/{{params.id}}`, or the item's fields
   passed as params) as drafts, opened in the visual editor. Done when: TVMaze list + detail
   render on the emulator without editing JSON.
3. **W3** Field picker in the visual editor (probe the screen's source, click a field → its
   `{{binding}}`), and a form for the screen's data sources instead of the JSON field.

## Acceptance criteria
- With `https://api.tvmaze.com/shows`, the wizard produces list + detail screens that render on
  the emulator without editing JSON.
- Unit tests for the heuristics on 3 fixture APIs; the probe rejects private addresses.

## Docs to update
README (Etap 8), `protocol/AGENTS.md` (nothing new in the model expected), history.
