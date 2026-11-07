# compat-tests — AGENTS.md

Golden compatibility tests between server and Android player, as **screenshot tests**.

## What runs

`DemoScreensScreenshotTest`: `server-app`'s `demoScreens()` → `sdk-server` renders RC documents
(images from a generated gradient, no network) → `ScreenDocument.fromBytes` → `ComposeCastScreen`
(real `RemoteComposePlayer`) inside a Material3 `Scaffold` like `app-template` → Roborazzi capture.
Robolectric native graphics, `sdk = 35`, Pixel 7 qualifiers. No emulator.

Goldens: `src/test/screenshots/*.png` — also embedded in the root README.

```bash
./gradlew :compat-tests:verifyRoborazziDebug   # CI; on failure CI uploads build/outputs/roborazzi/
./gradlew :compat-tests:recordRoborazziDebug   # after an intentional visual change; commit PNGs
```

Look at the recorded PNGs before committing them — a green record proves nothing.

## Decisions

- Android library module with unit tests only (no main code); depends on `server-app` in test
  scope just for `demoScreens()`, so README shows exactly what the server serves.
- `changeThreshold = 0.0002` (0.02% of pixels): goldens are recorded on macOS, CI is Linux.
  Changing two words on a button is ~0.14%, so content changes are still caught (verified).
- Add a screen to the demo → add a `@Test` here and record.
- The Material3 `TopAppBar` frame is not the subject: its status-bar inset differs between
  Robolectric and devices (and changed with Compose upgrades). When a diff appears, check whether
  the RC content itself moved/changed before re-recording.
