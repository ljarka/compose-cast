# T-043 — App bar shows the screen id while loading

Status: ready · Roadmap: Etap 7 · Depends on: — · Module: app-shell

## Goal

Until the document arrives, the app bar shows the app name (or nothing), never the internal screen
id. Found by the weather app (T-025): "main" flashes in the app bar on every start.

## Design

`app-shell/.../ComposeCastActivity.kt:150`: `screenState?.getOrNull()?.title ?: screenId`. Keep the
previous screen's title while navigating? No — use the app name (`R.string.app_name`, set by the
plugin from `app.name`) while loading and on error.

## Acceptance criteria

- Emulator: the demo app shows its app name, then the screen title (Maestro `smoke.yaml` still passes).
- `./scripts/verify.sh` green.

## Docs to update

`app-shell/AGENTS.md`, history.
