# 0032 — Phone preview in the panel: the real player under Robolectric, as an optional service

- Status: Accepted
- Date: 2026-11-24
- Amends: [0013](0013-admin-preview.md) (option 2 "server-rendered PNG" was rejected because "no
  headless Compose renderer runs on a standard JVM"; that is no longer true). The QR preview on a
  real device (ADR 0013/0027/0030) stays.

## Context

T-048 (owner, 2026-11-24): show the screen being edited as the phone draws it, inside the admin
panel, without a phone or emulator. Building an app in the panel (mission, T-046) should not need a
device for every look.

Options:

1. **Browser RC player** (unofficial TypeScript port): unmaintained, behind the binary format, needs
   a JS build (ADR 0012). Rejected again.
2. **A JVM player of our own**: `remote-core` is a plain jar and has an abstract `PaintContext`; a
   Java2D implementation is possible. It would be a second renderer (text measuring, fonts, paths)
   that drifts from the real one, against the spirit of ADR 0001. Rejected.
3. **An HTML mock of the screen JSON**: fast, but it shows something other than what the phone draws.
   Rejected.
4. **The real player (`sdk-android`'s `ComposeCastScreen`) under Robolectric native graphics**, the
   setup our golden screenshots have used since ADR 0006, run as a long-lived process.

Measured (RC 1.0.0-alpha23, Robolectric 4.17, 2026-11-24):

- One sandbox, documents swapped through Compose state: **56–71 ms per screen** including PNG
  encoding (1078×2399), first render 132 ms. Real weather-app screens: 63–100 ms.
- Outside Gradle (`installRenderer`, plain `java`): ready in 4 s. In Docker linux/amd64 emulated on
  Apple silicon: ready in 9 s, 90–120 ms per render, **PNG byte-identical** to the macOS render.
  Image 523 MB, ≈ 730 MiB memory.
- Through the admin (draft → `document(json)` → renderer): edit saved → new image in **0.14 s**.

## Decision

- New module **`preview-renderer`**: an HTTP service (`POST /render`: RC document → PNG of a
  Pixel 7, 411×914 dp at 2.625x; `GET /health`). It plays documents with `ComposeCastScreen`, so the
  RC player API stays in `sdk-android` (ADR 0002).
- Robolectric only boots inside its test runner, so **the service is a never-ending test**
  (`RenderServer.serve`, skipped unless `-Dcomposecast.renderer.serve=true`), started by `main`
  through `JUnitCore`. Its runtime is the module's unit-test classpath, copied by `installRenderer`
  together with AGP's test config (manifest, resource apk, assets) and the android-all jar, and run
  offline (`robolectric.offline=true`). HTTP threads only queue work. The sandbox's main thread renders.
- Animations are settled before capture: `ShadowLooper.idleFor(3 s)` (the player's clock follows the
  looper clock), so a gauge shows its value, not its first frame.
- **Optional and separate.** Its own image `ghcr.io/ljarka/compose-cast-preview-renderer`,
  **linux/amd64 only** (`nativeruntime-dist-compat` 1.0.19 has no linux/arm64 build), compose
  profile `preview`. The server image stays small and multi-arch. The admin uses it only when
  `COMPOSECAST_PREVIEW_RENDERER_URL` is set. Without it, the editor is unchanged.
- The admin (`server-admin`) renders the stored draft (else live) to an RC document through
  `ScreenPublishing.document` (default params, live brand, real data), sends it to the renderer,
  caches PNGs by document hash (32), and shows them in a phone frame in the screen editor.
  Errors show an SVG message in the same frame.
- Architecture rule **R12**: Robolectric, and sdk-android next to sdk-server, only in `compat-tests`
  and `preview-renderer`.

## Consequences

- Owners see the screen as the app draws it in the panel, without a device. The QR preview remains
  for real hardware, gestures and live updates.
- It is a static snapshot of the first viewport: no taps, no scrolling, no app bar, and live screens
  are not streamed into the frame. Each save shows the new state.
- Linux arm64 servers (e.g. ARM VPS) need emulation (slow) or go without the frame until
  Robolectric ships an arm64 native runtime.
- ≈ 800 MB RAM for the extra container: documented as optional in `example.env`.
- Upgrades of RC, Compose or Robolectric now also change a production image. `verify.sh --renderer`
  (CI job `preview-renderer`) builds it and renders the demo `main`.

## How to verify it still holds

```bash
./scripts/verify.sh --renderer        # image builds for linux/amd64, renders main as 1078x2399 PNG
./gradlew :preview-renderer:testDebugUnitTest :server-admin:test --tests '*PhonePreviewTest*'
```
