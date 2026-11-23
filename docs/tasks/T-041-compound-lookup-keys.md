# T-041 — Lookups keyed by more than one value (day/night icons)

Status: done (commit pending) · Roadmap: Etap 4 · Depends on: — · Module: protocol, sdk-server

## Goal

A binding can pick a lookup entry from two values. Found by the weather app (T-025): the hourly
list shows ☀️ at 23:00 because `{{h.code | lookup:icon}}` cannot also use Open-Meteo's `is_day`.

## Design (recommended; confirm in the task before coding)

- New filter `append:<arg>`: appends a string to the value. `<arg>` is either a quoted literal
  (`append:'-'`) or a **path** in the same scope (`append:h.is_day`). Then
  `{{h.code | append:'-' | append:h.is_day | lookup:icon}}` with keys `"0-1": "☀️"`, `"0-0": "🌙"`.
- Path arguments are new: today every filter argument is a literal. Validate them like template
  roots (unknown root → error with the JSON path) in `protocol/ScreenValidation.kt`.
- `lookup` keeps its `"*"` fallback; consider a second fallback step (`"0-*"`) only if needed.

Alternative considered: a `when`/conditional component — larger, belongs to T-020.

## Steps

1. `protocol/Binding.kt` (`Template` parser: path arguments, `Filters.ARITY`), validation.
2. `sdk-server/Binder.kt` (filter implementation, resolving path arguments in the item scope).
3. Tests: `BindingTest`, `DataBindingTest` (zip repeat with code + is_day).

## Acceptance criteria

The weather app's hourly screen shows 🌙 for clear night hours (verify on the emulator).

## Docs to update

`docs/screen-json-format.md` (filters), history.

## Result (2026-11-23)

- As designed: `append:<arg>`; an unquoted argument with `.` or `[` is a path (`FilterCall.paths`),
  bare names stay literals (`lookup:icon`), so nothing existing changes meaning. Paths are allowed
  in `append` and `default` (`Filters.PATH_ARGUMENTS`), validated like template roots (unknown
  root, params, no secrets); a missing path gives `""`. No `"0-*"` fallback: the weather app lists
  both `code-1` and `code-0` keys (generated table).
- Tests: `BindingTest` (parsing, validation), `DataBindingTest` (zip of code + is_day).
- Weather app: `hourly.json` on branch `next` of ljarka/weather-app (needs a server with T-041, so
  it is merged after the next release); verified on the emulator with a locally built image.
