# T-008 — Spike: local state in procedural RC API

Status: queued (after T-003) · Roadmap: Etap 5 / Etap 10 · Depends on: none (independent spike)

## Research result (2026-11-07) — questions 1–4 answered from sources
**Yes, accessible.** The procedural `RcScope` DSL in `remote-creation-core` alpha23 has mutable
variables (`remoteInteger`, `remoteNamedInteger`, `remoteBool`), `setValue` inside
`Modifier.onClick { }`, `StateLayout(stateIndex)` and `CollapsibleRow/Column` — details and file
locations in [docs/remote-compose.md → State API — procedural](../remote-compose.md#state-api--procedural-verified-in-remote-creation-core-100-alpha23-sources).
What remains is the **proof of concept** (steps 3 below), which needs device verification.
Steps 1–2 and the "not accessible" branch (step 4) are obsolete.

## Goal

Determine whether the procedural `createRcBuffer` DSL (`remote-creation-core` + `remote-creation-jvm`)
exposes local-state primitives, and if so, implement a minimal proof-of-concept.

## Why

Currently all compose-cast documents are **fully static**: every user interaction goes through
`hostAction` → Android HTTP request → server renders new `.rc` → round trip. This adds latency for
every tap.

Remote Compose is designed with **local state management** on the player (confirmed in official docs,
2026-11-07). State-driven components (`RemoteStateLayout`, `RemoteCollapsibleRow/Column`,
`rememberMutableRemoteInt`) handle interactions **without a server round-trip**: expand/collapse,
toggle, in-place counter. These improve responsiveness and enable offline resilience.

The uncertainty: the higher-level `@RemoteComposable` / `remote-creation-compose` API likely requires
a Compose runtime (Android or CMP). compose-cast's server uses `remote-creation-core` (pure JVM).
We need to confirm whether the procedural DSL exposes the same state machinery.

## Questions to answer

1. Does `createRcBuffer` DSL expose any of: `rememberMutableRemoteInt`, `rememberMutableRemoteFloat`,
   `rememberMutableRemoteString`, `rememberNamedRemoteColor`?
2. Is there a procedural equivalent of `RemoteStateLayout` (branching on remote state)?
3. Are `RemoteCollapsibleRow` / `RemoteCollapsibleColumn` accessible from the procedural API?
4. If 1–3 are not in the DSL — are there lower-level operations in `remote-creation-core` that the
   higher-level API wraps? (Check source JARs: `RemoteComposeWriter` operations, `RcScope`.)

## How to research

```bash
# Download and unpack sources
V=1.0.0-alpha23
mkdir /tmp/rc-sources && cd /tmp/rc-sources
for A in remote-creation-core remote-creation-jvm remote-creation-expression; do
  curl -sfLO https://dl.google.com/android/maven2/androidx/compose/remote/$A/$V/$A-$V-sources.jar
  unzip -q $A-$V-sources.jar -d $A
done

# Search for state-related symbols
grep -r "MutableRemote\|RemoteState\|rememberMutable\|Collapsible\|StateLayout" /tmp/rc-sources/ | head -40

# Also check remote-creation-compose (may have different deps):
curl -sfLO https://dl.google.com/android/maven2/androidx/compose/remote/remote-creation-compose/$V/remote-creation-compose-$V.pom
cat remote-creation-compose-$V.pom
```

## Steps

1. Download and unpack `remote-creation-core`, `remote-creation-jvm`, `remote-creation-expression`
   sources (see above).
2. Answer questions 1–4. Document findings in `docs/remote-compose.md` under a new "State API —
   procedural" section.
3. If state is **accessible**: implement one minimal stateful component in `RemoteComposeRenderer.kt`
   (e.g. `Component.Expandable(title, content: List<Component>)` rendered with
   `remoteInteger(0)` + `StateLayout` + `onClick { setValue(state, 1 - state) }`). The model is
   `Component` in `protocol/.../Screen.kt` (T-002 replaced `ScreenNode`); follow
   [add-component.md](../playbooks/add-component.md) (model, validation, DSL, renderer, JSON,
   screenshot, emulator).
4. If state is **not accessible** from procedural API: document the blocker and evaluate whether
   `remote-creation-compose` can run on JVM (Compose Multiplatform). Create a new ADR (next free number) with the
   finding and chosen strategy (accept limitation / add CMP dependency / defer to RC stable release).

## Acceptance criteria

**Minimum** (always):
- `docs/remote-compose.md` updated with findings on state API availability.
- (Obsolete: state is accessible.)

**If state is accessible**:
- `Component.Expandable` added to the `protocol` model (+ JSON, validation, `ScreenJsonTest`).
- `RemoteComposeRenderer.kt` renders it using local state (expand/collapse without server call).
- `compat-tests` golden updated for the new component.
- `./scripts/verify.sh` green.

## Verification

```bash
./scripts/verify.sh --quick
# If state works: add expandable section to a test screen and verify it toggles on device
# without a network request: `adb logcat -s ComposeCast | grep REQUEST` and the server log
# (`GET - /screens`) must not grow when expanding/collapsing
```

## Docs to update

- `docs/remote-compose.md` — "State API — procedural" section
- `docs/tasks/README.md` — mark done; if state works, update T-007 to include stateful components
- `docs/history.md` — entry for this spike
- `protocol/AGENTS.md` — `Component.Expandable`; README DivKit table row "States"

## Out of scope

- Full state management system (server-push state sync, optimistic updates)
- Multiple state variables per screen (this spike = one proof-of-concept)
- `RemoteCanvas` custom drawing
