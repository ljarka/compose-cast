# T-034 — Forms: input fields, validation, submit to an API/webhook

Status: queued (after T-021) · Roadmap: Etap 10 · Depends on: T-021 (text input via RC `Custom`) · Module: server-forms, protocol, sdk-server, app-shell

## Goal
Apps can collect data: contact forms, sign-ups, surveys, comments. Components: `textField`
(single/multi-line, keyboard type), `checkbox`, `switch`, `select`, `submitButton`. Action
`submit` posts the values to the compose-cast server, which forwards them to a configured target
and then navigates or shows a message.

## Why
Today apps are read-only, and many app ideas need input (owner, 2026-11-16).

## Design
- Values live on the device (RC named variables / custom component state, see T-021, T-017). On
  submit the shell collects them by field id.
- `POST /screens/{id}/forms/{formId}` (JSON body) → the server validates against the form
  definition (required, length, regex, email) → the **target**: an HTTP request template (like
  data sources: URL, headers, body template `{{form.email}}`, `{{secret.X}}`), a webhook, or
  storage in the server's database (a "submissions" table viewable and exportable in the
  admin panel). Response: `next` action or field errors shown under the fields.
- Validation runs on the server; client-side validation (T-020 expressions) is optional.
- Rate limiting per device/IP; optional honeypot field.

## Acceptance criteria
- A demo contact form that sends to the database and to a webhook (test server); field errors are
  shown on the emulator; a screenshot test of the form states.

## Docs to update
`protocol/AGENTS.md` (components, action), README (Etap 10), admin docs (submissions), history.
