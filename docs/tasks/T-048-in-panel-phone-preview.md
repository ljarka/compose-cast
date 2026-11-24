# T-048 — Phone preview inside the admin panel (server-rendered PNG)

Status: blocked: owner picks its place in the roadmap · Roadmap: not placed yet (fits Etap 8, next to G3) · Depends on: T-032, ADR 0013, ADR 0030 · Module: new `preview-renderer`, server-admin, compat-tests

## Goal
The screen editor (JSON and visual) shows the screen **as the phone would draw it**, in a phone
frame beside the form, with no phone or emulator. It refreshes on every saved draft. The QR preview
on a real device (ADR 0013/0027/0030) stays for checks on real hardware.

## Why
Owner, 2026-11-24: preview the mobile app screen in the admin panel. Mission: a non-developer
builds a whole app in the panel (T-046). Needing a phone for every look slows that down. ADR 0013
rejected a server-rendered PNG because "no headless Compose renderer runs on a standard JVM".
The spike below shows that Robolectric is such a renderer, already trusted for our goldens
(ADR 0006). Building this needs a new ADR that amends 0013.

## Spike (2026-11-24, RC 1.0.0-alpha23, Robolectric 4.17, native graphics, Pixel7 qualifiers, macOS arm64)
A throwaway test in `compat-tests` rendered 6 demo screens 30 times in one Robolectric sandbox.
It set the `ScreenDocument` through Compose state, then ran `waitForIdle`,
`onRoot().captureToImage()` and PNG encoding:
- first render 132 ms, then **56–71 ms per screen including PNG encoding**. A 1078×2399 PNG is
  80–155 KB. Heap ≈ 110 MB. Whole JVM including sandbox start: ≈ 15 s wall in Gradle.
- Output was identical to the goldens (real `RemoteComposePlayer`, same as README images).
- Animated content (the `dashboard` gauges) is captured mid-animation on the first frame: the
  first dashboard PNG differed from later ones (123 KB vs 85 KB). The renderer must advance the
  clock until idle (e.g. `mainClock.advanceTimeBy` a few seconds) or capture after animations.
- **Platform limit:** `nativeruntime-dist-compat` 1.0.19 has native graphics only for
  `linux/x86_64`, `mac/aarch64`, `mac/x86_64` and `windows/x86_64`. There is **no Linux arm64**. The
  server image is multi-arch (`release.yml`: amd64 + arm64), and the owner's local Docker on Apple
  silicon runs arm64. The renderer image is therefore amd64 only (arm64 hosts: Docker's amd64
  emulation, slower, or no in-panel preview).

## Design (to confirm in step 1 and the ADR)
- **Separate service, optional.** New module `preview-renderer`: a JVM process that keeps one
  Robolectric sandbox warm and serves `POST /render` (RC bytes + width/height dp + density + dark?)
  → `image/png`. A separate container (`docker compose` profile `preview`, image amd64 only), so
  the main server image stays small and multi-arch. Robolectric and android-all are hundreds of MB
  and start in about 10 s.
- **The admin** gets `PREVIEW_RENDERER_URL`. When it is unset or the service is down, the editor
  shows only the QR code, as it does today. On every saved draft (the same hook as
  `ScreenPublishing.updatePreview`), an HTMX request reloads `<img src="/admin/screens/{id}/preview.png?rev=…">`.
  The server-admin side renders the draft to RC (it already does this for the preview registry),
  sends it to the renderer, and caches by document hash.
- Data sources: same as the QR preview (real API calls, the draft's own data). Images are already
  embedded server-side (ADR 0003).
- Device sizes: phone (Pixel7 values) first, then a tablet toggle.
- Rule 2 holds: the RC player API is used only through `sdk-android` (`ComposeCastScreen`). Add a
  `check-architecture.sh` rule saying `preview-renderer` is the only non-test place where
  `sdk-android` and Robolectric meet.

## Steps
1. **Service spike (open question):** run Robolectric outside Gradle as a long-lived process.
   Candidates: (a) a "test" that never ends, started with `JUnitCore` from a `main`, and serves HTTP
   from the sandbox's main thread through a request queue; (b) Robolectric's sandbox API directly.
   Needs android-all-instrumented in the image (Robolectric offline mode:
   `robolectric.offline=true`, `robolectric.dependency.dir`). Measure image size, cold start and
   warm render in Docker on linux/amd64. Write the ADR (amends 0013) with the numbers.
2. `preview-renderer` module + Dockerfile + compose profile; health check; settle animations
   before capture.
3. Admin: `PREVIEW_RENDERER_URL`, phone frame + `<img>` in both editors, refresh on save, falls
   back to QR only.
4. Tests: renderer golden equals the `compat-tests` golden for `main` (same pixels within
   tolerance); admin test with a fake renderer; Maestro is not needed (no device). Look at the
   PNGs (rule 4).

## Acceptance criteria
- Edit a text in the visual editor and save: the phone frame shows the change in under 2 s.
- Without the renderer the editor works exactly as today (test).
- The renderer image builds and runs on linux/amd64 in CI (`verify.sh --docker` or a new job).

## Docs to update
New ADR (amends 0013), `docs/admin-editor.md`, `server-admin/AGENTS.md`, new module `AGENTS.md`,
root module map, `docs/feature-checklist.md` (line "in-browser RC player later"), distribution
docs (`example.env`, compose profile), history.

## Out of scope
Clicking inside the preview (actions/navigation), live screens streaming in the frame (a static
snapshot per save is enough), Linux arm64 native rendering (wait for Robolectric).
