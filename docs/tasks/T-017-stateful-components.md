# T-017 — Stateful components: tabs, toggles, expandable, visibility, animations

Status: queued (after T-019) · Roadmap: Etap 10 · Depends on: T-008 (local state PoC), T-012

## Goal

Interactive components that change on the device without a server round trip: `expandable`,
`tabs`, `switch`, `checkbox`, `radioGroup`, `segmented`, conditional visibility, animated
transitions — and host-settable named variables (app → document).

## Why

RC alpha23: `remoteInteger/Bool/Float`, `remoteNamedInteger` (host can set it),
`Modifier.onClick { setValue(...) }`, `StateLayout(stateIndex)`, `Modifier.visibility(RcInteger)`,
`Modifier.animationSpec(...)`, `defineVisibilityAnimation` (`docs/remote-compose.md` → State API).
T-008 proves the mechanism with one `Expandable`; this task generalizes it.

## Design (refine after T-008 results)

- Screen-level `state` declarations in the model: `{ "name": "tab", "type": "int", "initial": 0 }`;
  components reference state by name (validated: unknown name = error).
- `tabs { tab(label) { children } }` → header row with `onClick { setValue(tab, i) }` +
  `StateLayout(tab)`; selected tab styling via expression colors (verify how).
- `switch`/`checkbox`/`radioGroup`/`segmented` = drawn controls (Box/Canvas) toggling a state
  variable; the value is sent to the server only with an action (`hostAction` with `valueId` —
  see "Behaviors that bit us" #5).
- `modifier.visibleWhen: { state, equals }` → `Modifier.visibility(expression)`.
- Animations: `animation: { durationMs, easing }` on state-driven components if
  `animationSpec` works on the View player (verify; else document as unsupported).
- Named variables the app can set (`remoteNamedInteger`) — design only the protocol name here;
  the SDK API (`ComposeCastScreen.setVariable`) is Etap 10.

## Steps

T-008 results first → ADR if the model shape changes (state declarations are a model change →
ADR 0015 follow-up) → model → DSL → renderer → tests → demo `interactive` (+ JSON) → screenshot of
each state (record multiple goldens by setting initial values) → emulator: toggling makes **no**
HTTP request (server log unchanged).

## Acceptance criteria

- Tabs, switch, checkbox, radio, expandable work on the emulator with no network request.
- A switch value reaches the server inside a `navigate`/`custom` action on submit.
- `./scripts/verify.sh` green; goldens reviewed.

## Docs to update

ADR (if needed), `docs/remote-compose.md` (StateLayout/visibility/animation behavior, version),
`protocol/AGENTS.md`, `sdk-server/AGENTS.md`, `sdk-android/AGENTS.md` (if the SDK changes), README
Etap 10 boxes, `docs/history.md`.

## Out of scope

Text input (T-021), expressions over API data (T-006/T-020), persistent state across screens.
