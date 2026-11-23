# T-046 — Milestone 2: TV shows app built only in the admin panel

Status: queued (after T-021) · Roadmap: specs/roadmap.md F3 (success criterion 1) · Depends on: T-007, T-045, T-029, T-030, T-031, T-010, T-027, T-021 · Module: external repository (+ generic fixes)

## Goal

Prove the mission's first success criterion: a [TVMaze](https://www.tvmaze.com/api) app (search,
show list, show detail, episodes; no API key) built **only in the admin panel**, exported
(T-010 / [ADR 0025](../decisions/0025-owner-app-built-by-github-actions.md)) to its own repository
and built into a signed AAB by GitHub Actions, without Android Studio.

## Done when

- Signed AAB produced by the exported repository's workflow; app runs on the emulator (Maestro).
- Total time and every point where a non-developer would get stuck written down; gaps become tasks
  (like T-040…T-044 after the weather app).
- Screenshots in the README; Etap 9 boxes in `docs/feature-checklist.md` ticked.
