# Playbook — add a component to the screen DSL

Example: `divider(thickness = 1)`. Read first: [ADR 0002](../decisions/0002-confine-restricted-remote-compose-api.md),
[ADR 0004](../decisions/0004-units-dp-and-sp.md), [ADR 0006](../decisions/0006-screenshot-tests-as-goldens.md),
[docs/remote-compose.md](../remote-compose.md).

1. **Find the RC primitive** in the sources jar (`RcScope.kt`, `Modifier.kt` in
   `remote-creation-core`; layout in `remote-core/operations/layout`). Note the version-specific
   behavior you rely on in `docs/remote-compose.md`.
2. **Model** — `protocol/.../Screen.kt`: add a `Component` subtype with `@SerialName("<json
   name>")`; new fields need defaults where sensible (stored JSON, [ADR 0015](../decisions/0015-screen-model-is-the-product.md)).
   Validate content in `ScreenDefinition.problems()` (message with the `components[i].field` path)
   so mistakes fail at server start, not on devices; test it in `ScreenJsonTest`.
   **DSL** — `sdk-server/.../ScreenDsl.kt`: add the `ScreenBuilder` function that appends it.
3. **Renderer** — `sdk-server/.../RemoteComposeRenderer.kt` (the only file allowed to use the RC
   creation API): add the `renderNode` branch. Units per ADR 0004: sizes as dp constants, fonts via
   `sp()`, height expressions wrapped in `written(...)`.
4. **Unit tests** — `RemoteComposeRendererTest`: the `describe()` dump contains the expected
   operations; invalid input throws `IllegalArgumentException`; no `Height(EXACT, 0.0)`.
5. **Demo + screenshot** — use the component in `server-app/.../Screens.kt` (`demoScreens()`), or add
   a demo screen plus a `@Test` in `compat-tests/.../DemoScreensScreenshotTest.kt`.
   `./gradlew :compat-tests:recordRoborazziDebug`, **open the PNG and check it**, then
   `verifyRoborazziDebug`.
6. **Device** — [verify-on-emulator.md](verify-on-emulator.md).
7. **JSON** — if the demo screens use it, update the JSON copy in
   `examples/demo/screens/` (`JsonScreensTest` prints the expected content).
8. **Docs** — `sdk-server/AGENTS.md` (usage), README "Defining screens" and roadmap box,
   `docs/history.md`.
9. `./scripts/verify.sh`.
