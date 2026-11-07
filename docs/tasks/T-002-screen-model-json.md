# T-002 — Serializable screen model + JSON screens

Status: queued (after T-001) · Roadmap: Etap 2 · Depends on: T-001

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
