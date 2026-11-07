# 0015 — The screen model is the product

- Status: Accepted
- Date: 2026-11-07

## Context

Screens must come from several sources: the Kotlin DSL (now), JSON files on a volume (Docker),
the admin panel (Etap 8, storage = JSON files, [ADR 0011](0011-admin-storage-format.md)) and
possibly scripts. They may be rendered by more than one renderer: Remote Compose for Android now,
maybe DivKit for iOS ([ADR 0007](0007-ios-strategy-spike.md)). Data bindings
([ADR 0008](0008-server-side-data-sources.md)) need a place in the same definition. Until T-002
the only model was an internal `ScreenNode` list inside `sdk-server`, reachable only from the DSL.

## Decision

- One serializable model, `ScreenDefinition` / `Component` / `TextStyle`, in
  `protocol/.../Screen.kt`, with `Action` from T-001 for behavior. It is the source format: every
  authoring path produces it, every renderer consumes it. It is **not** sent to apps (Android
  receives RC documents, rule 1 in AGENTS.md).
- It lives in `protocol` (pure Kotlin + kotlinx.serialization, rule R4), so the admin server and
  any future renderer can use it without the RC-dependent `sdk-server` (as ADR 0011 requires).
- JSON format (`ScreenJson`): `"type"` discriminator for components and actions, defaults omitted,
  **strict** (unknown keys, component types or action types are errors). Canonical form = what
  `ScreenJson.encode` prints.
- Validation lives in one place, `ScreenDefinition.problems()` / `validate()`, with JSON-like paths
  (`components[2].action: …`). DSL, JSON loader and admin panel all use it. Only the DSL-specific
  parsing (text style key, wire-form action string) fails earlier, in `ScreenBuilder`.
- An action must survive the wire: `ActionCodec.decode(encode(a)) == a` and ≤ 4000 characters.

## Consequences

- A new component = a `Component` subtype (JSON name via `@SerialName`) + a DSL function + a
  renderer branch ([add-component playbook](../playbooks/add-component.md)).
- Changing a component's fields changes the stored JSON format: add fields with defaults only;
  renaming/removing needs a migration note in `docs/history.md` (ADR 0011).
- Because the model is in `protocol`, it is also on the Android app's classpath (small, unused
  there). Acceptable; revisit if `protocol` grows server-only weight.

## How to verify it still holds

`./gradlew :server-app:test` — `JsonScreensTest`: the JSON copy of the demo screens
(`examples/demo/`) equals the DSL definitions, is canonical, and renders
byte-identical RC documents. `grep -rn "ScreenNode" --include=*.kt .` finds nothing.
