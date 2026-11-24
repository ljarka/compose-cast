# preview-renderer — AGENTS.md

The optional service behind the admin panel's phone preview (T-048,
[ADR 0032](../docs/decisions/0032-phone-preview-rendered-by-robolectric.md)): it plays Remote
Compose documents with `sdk-android`'s real player under Robolectric native graphics and returns
PNGs. The admin calls it when `COMPOSECAST_PREVIEW_RENDERER_URL` is set (`server-admin` →
`PhonePreview.kt`).

## Scope

This module only. It is the one place outside `compat-tests` where Robolectric is used and
`sdk-android` sits next to `sdk-server` (rule R12). No RC API here: documents are played through
`ComposeCastScreen` (ADR 0002).

## Files

| File | Purpose |
|---|---|
| `src/test/.../RenderServer.kt` | The service: a Robolectric "test" whose `serve()` never returns (skipped unless `-Dcomposecast.renderer.serve=true`). JDK `HttpServer` on `PORT` (8090): `GET /health` (200 after the warm-up render), `POST /render` (RC bytes ≤ 16 MB → `image/png`; 400 not a document, 413, 503 timeout 20 s). HTTP threads queue jobs; the test's main thread renders. |
| `src/test/.../Renderer.kt` | `render(bytes)`: `ScreenDocument.fromBytes` → `ComposeCastScreen` (fresh player per document via `key`) → `waitForIdle` → `ShadowLooper.idleFor(3 s)` (settle animations) → `captureToImage` → PNG |
| `src/test/.../Main.kt` | `main`: sets the serve property, runs `RenderServer` with `JUnitCore`; `PHONE` qualifiers (Pixel 7: 411×914 dp, 420 dpi → 1078×2399 px) |
| `src/test/.../RendererTest.kt` | Size, determinism with a gauge (animation settled), a change changes pixels, garbage → `IllegalArgumentException`; writes `build/renderer-test.png` (look at it) |
| `build.gradle.kts` | `installRenderer` → `build/renderer/`: `lib/` (unit-test classpath, library jars deduplicated), `config/` (a `test_config.properties` pointing at `android/`), `android/` (merged manifest, resource apk, assets), `android-all/` (android-all-instrumented 15 for offline mode), `classpath.txt`, `bin/preview-renderer` |
| `Dockerfile` | `eclipse-temurin` JRE on Ubuntu (glibc: the native runtime needs it), **linux/amd64 only**, `USER ubuntu`, `JAVA_OPTS=-Xmx512m`, healthcheck |

## Commands

```bash
./gradlew :preview-renderer:testDebugUnitTest                 # RendererTest (RenderServer is skipped)
./gradlew :preview-renderer:installRenderer && preview-renderer/build/renderer/bin/preview-renderer
curl --data-binary @main.rc localhost:8090/render -o main.png  # main.rc = curl <server>/screens/main
./scripts/verify.sh --renderer                                 # image build + render demo main (CI job)
```

## Decisions

- **Why a test:** Robolectric boots only inside its runner with AGP's generated test config, so the
  unit-test classpath *is* the runtime. `installRenderer` reproduces what Gradle's test task hands
  Robolectric. Keep the copied config's relative paths (cwd = `build/renderer`, set by the script).
- **sdk-server is on the classpath** (test dependency) to build the warm-up document at start-up in
  the current format, instead of a committed `.rc` file that RC upgrades would break.
- The test classpath lists every library jar twice (Gradle cache + transformed copy, 150 MB native
  runtime too): `installRenderer` keeps the first per file name for cache jars, all project outputs
  (several are named `classes.jar`). 447 MB on disk, image 523 MB.
- **Classes load twice:** `Main` runs in the app class loader, `RenderServer`/`Renderer` in the
  sandbox's. Never pass state through statics between them (system properties work).
- One phone size for now (the Robolectric qualifiers are per sandbox). A tablet frame needs a second
  sandbox config or `RuntimeEnvironment.setQualifiers` + a new activity: decide in a follow-up.
- Numbers (2026-11-24, ADR 0032): warm render 56–71 ms on macOS, 90–120 ms in Docker amd64
  emulated on Apple silicon, PNG identical, ≈ 730 MiB memory, ready in 4 s (9 s in Docker).

## Dependencies

`:sdk-android` (player), `:sdk-server` (warm-up/test documents), Compose foundation + ui-test,
Robolectric 4.17 (+ `robolectric-android-all` in the `androidAll` configuration), JUnit 4.
