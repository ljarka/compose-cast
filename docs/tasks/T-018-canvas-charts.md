# T-018 — Canvas: shapes, progress indicators, charts, gradients

Status: queued (after T-016) · Roadmap: Etap 5 · Depends on: T-012

## Goal

Server-defined drawings: progress bar/ring, rating stars, line/bar/area charts (e.g. hourly
temperature for the weather app), simple shapes, gradient backgrounds.

## Why

RC alpha23 `Canvas { }` has `drawRect/Circle/Line/Arc/Sector/Oval/RoundRect/Path/TweenPath`,
text on path/circle, bitmaps, paint (`RcPaintScope`: color, stroke, caps), shaders for gradients
(`RcShaderScope`, `createShader`), paths (`remotePath`, `remoteXYPath`, `remotePolarPath`), and
float arrays with `arraySpline`/`arrayMax` (`docs/remote-compose.md` → Procedural catalog).
Charts are a key DivKit-class feature and need no native code.

## Design

- High-level components (preferred — the admin panel can edit them):
  `progress { value 0..1, style: bar|ring, color, trackColor }`,
  `chart { kind: line|bar|area, values: [..], labels: [..], color, height }`,
  `rating { value, max }`.
- Gradients: `modifier.background` accepts `{ "gradient": "linear|radial", "colors": [...],
  "angle" }` (verify shader support in the View player).
- Low-level `shape { kind: rect|circle|line|path(svgPathData?), fill, stroke }` only if needed by a
  real screen — decide in the task; avoid exposing raw draw ops in the model (ADR 0015: semantic
  model).
- Chart values are static now; T-006 binds them to API arrays.

## Steps

Read `RcPaintScope.kt`, `RcShaderScope.kt`, `RcPath.kt`, a canvas sample in RC sources/guide
(`PROCEDURAL_COMPONENTS_GUIDE.md`) → model → renderer (all coordinates via
`componentWidth()/componentHeight()` expressions so charts scale) → tests → demo `charts` (+ JSON)
→ screenshots → emulator.

## Acceptance criteria

- Progress bar/ring, line + bar chart with 24 values, rating and a gradient card render and scale
  with width (check two widths).
- `./scripts/verify.sh` green; goldens reviewed.

## Docs to update

`docs/remote-compose.md` (canvas/shader behavior, version), `protocol/AGENTS.md`,
`sdk-server/AGENTS.md`, README Etap 5 box + screenshot, `docs/history.md`.

## Out of scope

Animated drawings driven by time/touch (T-019/T-020), meshes and particles (T-022).
