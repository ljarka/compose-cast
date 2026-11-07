# T-002 — Serializable screen model + JSON screens

Status: done (`8e3e935`) · Roadmap: Etap 2 · Depends on: T-001

## Outcome (2026-11-07)
Implemented; `./scripts/verify.sh` green, screenshots unchanged, emulator renders as before.
Deviations from the design below:
- **The model lives in `protocol/.../Screen.kt`, not `sdk-server`** — ADR 0011 (accepted while
  T-002 was queued) requires the admin server to use `ScreenDefinition` without `sdk-server`.
  Recorded in [ADR 0015](../decisions/0015-screen-model-is-the-product.md).
- Validation = `ScreenDefinition.problems()` / `validate()` with `components[i].field` paths; an
  action must round-trip through `ActionCodec` (catches empty names/keys/URLs).
- Loader `screensFrom(dir)` requires `<id>.json` file names (ADR 0011 layout); also added
  `screen(definition)`, `brand(BrandConfig)`, `RemoteScreen.definition` (export DSL → JSON).
- The JSON copy later moved to `examples/demo/` (servable data dir, see server-app/AGENTS.md).
- `JsonScreensTest` additionally checks the files are canonical (`ScreenJson.encode` output).

## Goal
One serializable screen model that the Kotlin DSL builds **and** that can be loaded from JSON files,
so screens can come from files on a volume (Docker), the admin panel (Etap 8) and scripts, and be
rendered by any renderer (RC now, possibly DivKit for iOS — ADR 0007).

## Why
ADR 0007 (model independent of RC), ADR 0008 (same definition format for DSL and admin panel),
README Etap 2.

## Design
- New package `io.composecast.server.model` in `sdk-server` (public, `@Serializable`):
  ```kotlin
  @Serializable data class ScreenDefinition(val id: String, val title: String = id, val components: List<Component>)
  @Serializable sealed interface Component {
      @Serializable @SerialName("text")   data class Text(val text: String, val style: TextStyle = TextStyle.Body)
      @Serializable @SerialName("image")  data class Image(val url: String, val alt: String = "")
      @Serializable @SerialName("button") data class Button(val label: String, val action: Action)
      @Serializable @SerialName("spacer") data class Spacer(val height: Int = 8)
  }
  ```
  `TextStyle` becomes `@Serializable` with `@SerialName("h1")` etc. JSON class discriminator:
  `"type"`. Example:
  ```json
  { "id": "main", "title": "Home", "components": [
      { "type": "text", "text": "Welcome", "style": "h1" },
      { "type": "button", "label": "Get started", "action": { "type": "navigate", "screen": "onboarding" } } ] }
  ```
- `ScreenNode` (internal) is **replaced** by `Component`; the DSL builds `ScreenDefinition`s; the
  renderer renders `ScreenDefinition` (no RC types in the model — rule R1 still holds).
- Validation lives in one place (`ScreenDefinition.validate()` or a `ScreenValidator`), used by
  the DSL and the JSON loader: duplicate ids, empty id, negative spacer, colors, unknown action
  (an unknown action `type` in JSON is a decoding error naming the file — `Action` has no unknown
  subtype, see T-001).
- Loading: `composeCast { screensFrom(directory: Path) }` (or `ScreenRegistry` builder option) loads
  every `*.json` file; a JSON error names the file and the JSON path.
- `server-app`: keep `demoScreens()` in Kotlin; add an equivalent JSON copy under
  `server-app/src/test/resources/screens/` used by a test proving both paths produce identical RC
  bytes.
- `sdk-server` needs the `kotlin-serialization` plugin + `kotlinx-serialization-json`.
- `Action` (from T-001, `protocol`) is already `@Serializable` with discriminator `type`:
  `{"type":"navigate","screen":"onboarding","params":{"id":"42"}}` — verified by
  `ActionCodecTest` "actions serialize to JSON". Reuse it; do not invent another action format.

## Acceptance criteria
- DSL `demoScreens()` and the JSON files render byte-identical documents (test).
- JSON → model → JSON round trip is stable (test).
- Invalid JSON (unknown component type, missing field, bad action) fails with file + path in the
  message (tests).
- Public DSL source compatibility: `Screens.kt` compiles unchanged.
- Screenshots unchanged.

## Verification
`./scripts/verify.sh`.

## Docs to update
New ADR "Screen model is the product" (supersedes nothing; referenced by 0007/0008), `sdk-server/AGENTS.md`
(model, JSON format, loader), README "Defining screens" (JSON example), `docs/history.md`, T-003 check.

## Out of scope
Data binding fields (T-006), admin panel storage API (Etap 8), new components (Etap 5).
