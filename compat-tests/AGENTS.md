# compat-tests — AGENTS.md

End-to-end screenshot tests: the server renders demo screens to RC documents; the Android player
(Robolectric + native graphics) plays them; Roborazzi captures PNGs as golden files.

## Scope

This module only. Touches both `sdk-server` and `sdk-android` at test time — that is intentional; only here and in
`preview-renderer` (the admin's phone preview, same Robolectric setup) may the two share a classpath (rule R12).

## Key files

| File | Purpose |
|---|---|
| `DemoScreensScreenshotTest.kt` | Renders `demoScreens()` with a fake `ImageLoader` (no network), captures PNGs |
| `src/test/screenshots/main.png` | Golden for the `main` screen (README image) |
| `src/test/screenshots/onboarding.png` | Golden for the `onboarding` screen (README image) |
| `src/test/screenshots/actions.png` | Golden for the `actions` screen: one button per action type (README image) |
| `src/test/screenshots/brand-colors.png` | Golden for a DSL screen with brand surface/text/outline colors (T-040) |
| `ComposeCastFlowTest.kt` | T-059: the SDK's `ComposeCastFlow` against a **real** server (`server-app` `module()` on Netty, random port) and the real `ComposeCastClient`: tap → navigate with params, `onAction` interception, system Back, live screen socket open/close/reopen, error slot + Retry. Pumps the paused main looper while waiting (network answers resume there) |
| `src/test/screenshots/sdk-error-slot.png` | Golden for the SDK's default error slot (`ComposeCastDefaults.Error`, T-059) |
| `src/test/screenshots/echo.png` | Golden for the dynamic `echo` screen rendered with `word=zażółć gęślą jaźń` (README image) |

## Rules (ADR 0006)

- Goldens are the source of truth for rendering correctness.
- **Every new component gets a screenshot test** — add it to this module.
- A visual change that is intentional: re-record with `./gradlew :compat-tests:recordRoborazziDebug`,
  **look at the PNGs**, then commit.
- Pixel tolerance: 0.02% (`changeThreshold = 0.0002f`) — covers edge anti-aliasing differences
  between macOS (recording) and Linux (CI verification).
- `GeneratedImage` is the fake `ImageLoader`; it produces a deterministic gradient so tests don't
  depend on the network.

## Commands

```bash
# Verify (CI default)
./gradlew :compat-tests:verifyRoborazziDebug

# Re-record after an intentional visual change
./gradlew :compat-tests:recordRoborazziDebug

# Diff images are written to
# compat-tests/build/outputs/roborazzi/*_compare.png on failure
```

## Dependencies

- `:server-app` (testImplementation) — `demoScreens()`, so README shows exactly what the server serves
- `:sdk-server` (testImplementation) — `ImageLoader`
- `:sdk-android` (testImplementation) — `ComposeCastScreen`, `ScreenDocument`, `ComposeCastFlow`
- `ktor-server-netty`, `activity-compose` (testImplementation) — the real server and Back for `ComposeCastFlowTest`
- Roborazzi, Robolectric, Compose Test

## Decisions

- Android library module with unit tests only (no main code); depends on `server-app` in test
  scope just for `demoScreens()`, so README shows exactly what the server serves.
- `changeThreshold = 0.0002` (0.02% of pixels): goldens are recorded on macOS, CI is Linux.
  Changing two words on a button is ~0.14%, so content changes are still caught (verified).
- Add a screen to the demo → add a `@Test` here and record. Current tests include `main`, `onboarding`,
  `actions` (one button per action type), `dashboard` + `dashboard-tablet` (T-047 canvas components at two widths), `echo` (dynamic: `capture(id, params)` uses
  `registry.render(id, RenderContext(params))`).
- The Material3 `TopAppBar` frame is not the subject: its status-bar inset differs between
  Robolectric and devices (and changed with Compose upgrades). When a diff appears, check whether
  the RC content itself moved/changed before re-recording.
