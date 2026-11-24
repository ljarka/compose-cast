# History

What was done, in order, and where the reasoning lives. Numbers are the order of work — not the
README roadmap's "Etap" numbers.

1. **Initial state** (up to `64c58d1`): JSON `ScreenModel` + hand-written renderer, RC unused,
   Android never compiled → [ADR 0001](decisions/0001-remote-compose-wire-format.md).
2. **Server emits RC** (`2c92957`): `RemoteComposeRenderer`, binary `/screens/{id}`, `/debug`,
   build-time validation. `adc933d`: debug-only cleartext HTTP for the emulator (targetSdk ≥ 28
   blocks cleartext).
3. **App plays RC** (`acb2cbf`): `sdk-android` on `RemoteComposePlayer`, JSON renderer deleted,
   `expectSuccess = true`, back stack. Embedded images, units, `height` workaround
   ([0003](decisions/0003-embed-images-server-side.md), [0004](decisions/0004-units-dp-and-sp.md)).
4. **Handoff docs** (`9c75da7`).
5. **Cleanup** (`bbc76bb`): legacy JSON models removed, Android build + lint in CI.
6. **AGP 9.5.1 / Gradle 9.6.0** (`0166b43`): built-in Kotlin, no `kotlin-android` plugin.
7. **Screenshot tests** (`55190ca`): `compat-tests` → [ADR 0006](decisions/0006-screenshot-tests-as-goldens.md).
8. **Dependency refresh** (`6da87ff`): Compose BOM, Kotlin 2.4.21, Ktor 3.6.1, compileSdk 37 /
   targetSdk 36. Material3 TopAppBar shorter under Robolectric; RC content pixel-identical.
9. **Vision & roadmap** (`7623147`): DivKit-style framework on RC.
10. **RC alpha20 → alpha23**: units rewritten for the new density semantics (ADR 0004 table);
    screenshots unchanged with the fix, 42% diff without it.
11. **New goals** (owner): Android + iOS, admin panel, easy backend, components bound to external
    APIs, sample apps; documentation restructured for agents
    ([0007](decisions/0007-ios-strategy-spike.md), [0008](decisions/0008-server-side-data-sources.md),
    [0009](decisions/0009-documentation-for-agents.md)).
12. **Agent tooling** ([0010](decisions/0010-executable-rules-verify-tasks.md)):
    `scripts/check-architecture.sh` (8 rules), `scripts/verify.sh` (CI runs it), playbooks,
    task queue `docs/tasks/` with T-001…T-006.
13. **Cold-read test #1** (2026-11-07, [playbook](playbooks/cold-read-test.md)): a fresh agent found
    T-001 unaided, confidence 8/10. Fixed: `verify.sh` Android SDK fallback (failed in a fresh
    worktree), DoD vs "commit only when asked", T-001 wire grammar (raw `open_url`/`custom`, param
    order, 4000-char RC limit), `api(protocol)` in `sdk-android`, no `Action.Unknown` (decode → null,
    keeps T-002 JSON strict), demo `actions` screen for device checks, single `ready` status,
    tap-by-text in the emulator playbook, README Etap 2 order.
14. **Roadmap expansion** (`d9716a7`, `e2299f1`): full Etap 0–12 roadmap in README (DivKit mapping
    table, two app milestones, admin panel, iOS, data sources); Etap 12 Experimentation (A/B
    testing, feature flags, gradual rollout) added; admin panel governance (change history,
    rollback, screen variants) added to Etap 8.
15. **RC knowledge gap fixed**: `fontSize()` = 14sp assumption documented in
    `docs/remote-compose.md` (item 7) — silent failure risk on RC upgrades.
    Admin panel task stub added (`docs/tasks/T-007-admin-panel.md`).
16. **T-001 typed actions** ([task](tasks/T-001-typed-actions.md)): `Action` + `ActionCodec` in
    `protocol`, DSL/renderer/SDK/app migrated, demo `actions` screen + golden, Android client
    logging fixed (SLF4J had no binding).
17. **T-002 screen model** ([task](tasks/T-002-screen-model-json.md),
    [ADR 0015](decisions/0015-screen-model-is-the-product.md)): `ScreenDefinition`/`Component` +
    `ScreenJson` + validation in `protocol` (not `sdk-server`, per ADR 0011); DSL builds it,
    renderer renders it, `screensFrom(dir)` loads `<id>.json`; JSON copy of the demo screens
    renders byte-identical documents. CLAUDE.md rule table fixed to match `check-architecture.sh`.
18. **Review after T-002** (2026-11-07): server logged nothing (no SLF4J provider → logback +
    CallLogging + StatusPages JSON 500); registry built before startup (was lazy → invalid screens
    surfaced on the first request, not at start); `PORT` / `COMPOSECAST_DATA_DIR` env config and
    `examples/demo` data dir (JSON screens + brand, served without Kotlin); app: `OpenUrl` without a
    handler no longer crashes, 404 vs unreachable error + Retry (`ComposeCastHttpException` in the
    SDK); CLAUDE.md now imports AGENTS.md (the copy had drifted). Review notes for T-003 (swappable
    registry, image cache TTL/size limit, URL encoding) and T-007 (ADR 0011–0014 inconsistencies).
19. **T-008 local state spike added**: official RC docs (`developer.android.com`) confirmed
    `RemoteStateLayout`, `RemoteCollapsibleRow/Column`, `rememberMutableRemoteInt` exist.
    Unknown whether these are accessible from procedural `remote-creation-core` API (JVM).
    T-008 spike task added to investigate. T-007 admin panel explicitly scoped to static
    documents until T-008 resolves. Official component catalog added to
    `docs/remote-compose.md`.
20. **T-008 answered from sources + queue reprioritized** (2026-11-07): the RC state API
    (`remoteInteger`, `setValue` in `onClick`, `StateLayout`, `CollapsibleRow/Column`) is in the
    procedural alpha23 DSL we already use (docs/remote-compose.md); T-008 is now a PoC. Owner
    approved order: T-003 → T-009 Docker (new) → T-006 design → T-008 → Milestone 1 → T-004.
21. **ktlint** (2026-11-07): Spotless + ktlint 1.7 for all `*.kt`/`*.gradle.kts` from the root
    build (works with AGP 9 built-in Kotlin), rules in `.editorconfig` (`intellij_idea` style,
    120 columns, `@Composable` PascalCase); one formatting-only commit; `spotlessCheck` in
    `verify.sh`; CI badge. Why it was missed: the roadmap item had no task, and agents only take
    work from the queue → `check-docs.sh` now fails on open items in ✅/⏳ Etaps without `T-NNN`.
22. **`protocol` cleanup** (2026-11-07): removed never-used `BrandTokens`, `Variables`,
    `Headers.BRAND_ETAG` (placeholders from the initial structure). `protocol` keeps what server
    and app must share (`Action`/`ActionCodec`, `Headers`, `BrandConfig`) plus the screen model.
    Theming (README Etap 7) will design its tokens when it is implemented.
23. **T-003 per-request rendering** ([task](tasks/T-003-per-request-rendering.md)): params as query
    parameters (not path patterns: ids name `<id>.json`), `dynamicScreen` + `RenderContext`,
    ETag/304 end to end (server hash of bytes, SDK in-memory revalidation), swappable `LiveScreens`,
    thread-safe image cache with failure TTL, image URL scheme/size limits, app back stack with
    params; demo `echo` screen. Etap 2 complete.
24. **RC release watch**: `scripts/check-rc-release.sh` + weekly CI workflow (failed run = new RC
    release → release notes + upgrade playbook); linked from AGENTS.md and docs/remote-compose.md.
25. **Review of STATUS.md / T-010 / Etap 8–9** (2026-11-07): owner's SQLite decision recorded as
    [ADR 0016](decisions/0016-admin-storage-sqlite.md) (supersedes 0011/0014; screen JSON format and
    files mode kept; accounts instead of `ADMIN_PASSWORD`); T-010 fixed (exported ZIP could not build
    without SDK distribution, brand colors are server-side, HTTPS needed) and its step 1 split into
    T-011; Etap 8 preview aligned with ADR 0013; `docs/STATUS.md` now generated by
    `scripts/gen-status.sh` and checked by `check-docs.sh` (it was stale within an hour).
26. **Component coverage plan** (2026-11-07, owner: support every component Remote Compose can
    express): full procedural catalog of RC alpha23 (`RcScope`, `Modifier`, layout managers,
    View player `setCustomSupport`) recorded in `docs/remote-compose.md`; found no text-input op
    (→ native views via `Custom`, T-021). Tasks T-012…T-022 added; T-012/T-013 queued before
    Milestone 1, the rest after T-004; README Etap 5/10 boxes reference them.
27. **T-009 Docker** (2026-11-08): `server-app/Dockerfile` (temurin 21 JRE Alpine, non-root, busybox
    `wget` healthcheck, multi-arch with the build stage on `$BUILDPLATFORM`, 236 MB),
    `docker-compose.yml`, `verify.sh --docker` + CI job; server-only Gradle build
    (`-Pcomposecast.serverOnly=true`). Gotcha: Gradle-cache jars are 0600 → unreadable for the
    non-root user. Verified on the emulator (JSON edit + restart). Owner direction: apps live in their
    own repositories with no framework code changes →
    [ADR 0017](decisions/0017-apps-in-own-repositories.md), T-023; T-006 must be declarative.
    Committed `7405b52`.
28. **Distribution like Immich** (2026-11-08, owner: option (a), "similar like Immich"):
    [ADR 0018](decisions/0018-distribution-like-immich.md) — server installed from GitHub Release
    assets (`docker-compose.yml` + `example.env`), image on GHCR with `release`/`vX.Y.Z`/`main` tags,
    optional Caddy HTTPS profile; Android `protocol`/`sdk-android`/`app-shell` + Gradle plugin
    `io.composecast.app` on Maven Central so app repositories have no Kotlin. T-023 (server) and
    T-024 (Android) specified; T-010 export simplified.
29. **T-006 designed** (2026-11-08): data sources + bindings as part of the screen model (`params`,
    `data`, `lookups`, `error`, `repeat` over arrays or zipped parallel arrays, `{{path | filter}}`,
    `{{secret.X}}` = `COMPOSECAST_SECRET_X` only in source URLs/headers); binding is a model-to-model
    step, renderer unchanged; ADR 0008 accepted. Owner: Immich model confirmed, and everything
    app-specific must be editable in the admin panel (ADR 0017 rule 4). T-012's planned ADR
    renumbered 0017 → 0019 (0017/0018 taken).
30. **Self-hosted Maven repository** (2026-11-08, owner): apps stay branded, one repository each
    (Immich = backend model only); Android artifacts go to **Reposilite on the VPS** behind a
    Cloudflare Tunnel instead of Maven Central → [ADR 0019](decisions/0019-self-hosted-maven-reposilite.md)
    (supersedes that part of 0018), playbook `maven-repository.md`, T-024 updated. T-012's planned
    ADR renumbered again → 0020.
31. **CI `docker` job fixed** (2026-11-08): the first two runs failed because the Gradle wrapper
    inside the image build downloads Gradle from GitHub Releases, which returned 504 for every
    release asset (the `build` job passed thanks to the Actions cache). Reproduced in Linux
    Docker-in-Docker; the build stage now uses the `gradle:9.6.0-jdk21-noble` image (version checked
    against the wrapper). Found on the way: macOS `._*.json` files in a screens folder crashed
    startup → `screensFrom` skips hidden files.
32. **T-006 data sources** ([task](tasks/T-006-data-sources.md), [ADR 0008](decisions/0008-server-side-data-sources.md)):
    `params`, `data`, `lookups`, `error`, `repeat` (arrays or zipped parallel arrays) and
    `{{path | filter}}` templates in the screen model; `sdk-server` fetches in parallel with a
    per-URL cache (failures 10 s), binds model-to-model (renderer unchanged), 502 or `error`
    components on failure, secrets only from `COMPOSECAST_SECRET_*` and masked in logs. Demo `data`
    screen on live Open-Meteo, goldens with fixtures (`c6b0b3a`). Device check 2026-11-11 against the
    Docker image: live data, params navigation, back stack, cache + 304, error state. Etap 4 done;
    T-008 local state PoC is next.
33. **T-008 local state PoC (`Component.Expandable`)** ([task](tasks/T-008-local-state-spike.md)):
    `Component.Expandable(title, content, expanded = false)` in `protocol` (`@SerialName("expandable")`,
    validation, template bindings), `expandable(title, expanded) { … }` in `ScreenDsl`, rendered in
    `RemoteComposeRenderer` with `remoteInteger(0)` + `Box`-wrapped `StateLayout` + `Modifier.onClick { setValue(state, next) }`.
    Verified three alpha23 `StateLayout` behaviors in `remote-core` sources and on the emulator
    (recorded in `docs/remote-compose.md`): (1) `StateLayout` reads its state id only in `paint()`,
    so child `0` is always measured/painted on frame 1 (emit the initial branch at index `0`);
    (2) `StateLayout.paint()` does not execute direct `ComponentData` children (declare
    `remoteInteger(0)` before `StateLayout`); (3) `StateLayout.paint()` forgets `context.translate(mX, mY)`
    (wrap `StateLayout` in a `Box`). Added to the `onboarding` demo screen (both expanded and
    collapsed instances), golden re-recorded and emulator-verified (toggles locally with zero HTTP
    requests). Clarified in README/queue that the Milestone 1 weather app is an external example in
    its own repository, not part of this project (`0cc6cae`). T-011 is next.
34. **T-011 `app-template` configurable without code** ([task](tasks/T-011-app-template-config.md)).
    First version `ba52457` (properties → `BuildConfig`/`app_name`, release https check by task name),
    completed in `dc64663` (two sessions worked T-011 in parallel; the second replaced the first):
    `app-template/compose-cast.properties` (or `-Pcomposecast.appConfig=<path>`) sets name,
    `applicationId`, server URL, frame colors and an optional launcher icon; `build.gradle.kts`
    reads it with a self-contained `ComposeCastAppConfig` (missing/invalid key → build error naming
    it) and `GenerateLauncherIconTask` (adaptive icon, PNG per density, via
    `variant.sources.res.addGeneratedSourceDirectory`), both meant to move unchanged into the T-024
    plugin. Release variants fail before `pre<Variant>Build` unless `app.serverUrl` is `https://`.
    `MainActivity` uses `BuildConfig.SERVER_URL` and a frame color scheme (top app bar, status bar
    icon style via `enableEdgeToEdge`, window/splash background). New default adaptive icon. Verified
    on the emulator: demo and a second config (other id, amber frame, custom icon) installed side by
    side. T-023 is next.
35. **Maestro for UI clicking** (owner, 2026-11-12): emulator interaction uses Maestro (2.12.1)
    instead of hand-rolled `adb` taps; RC-rendered buttons are found by text. First flow
    `app-template/maestro/smoke.yaml` (launch → "Get started" → back) passes on the emulator (`dc64663`);
    [playbook](playbooks/verify-on-emulator.md) updated.
36. **T-023 server distribution like Immich** ([task](tasks/T-023-app-repositories.md),
    [playbook](playbooks/install-server.md)): `distribution/docker-compose.yml` (published image
    `ghcr.io/ljarka/compose-cast-server:${COMPOSECAST_VERSION:-release}`, read-only data dir, `.env`
    into the container for `COMPOSECAST_SECRET_*`, `https` profile = Caddy 2.10.2 `reverse-proxy`)
    and `distribution/example.env`; `.github/workflows/release.yml` (main → `:main`, release →
    `:<tag>` + `:release` + both files as release assets). No fixed project/container names, so
    several apps fit on one host. `verify.sh --docker` installs the distribution files into an empty
    directory against the built image. HTTPS verified locally with Caddy's internal CA
    (`COMPOSECAST_DOMAIN=localhost`: certificate obtained, http → 308 https, server on 127.0.0.1
    only). Open owner actions: make the GHCR package public, first (non-pre-)release, VPS check.
    T-024 is next (`2df0dc0`).
37. **T-024 Android artifacts + `io.composecast.app` plugin** ([task](tasks/T-024-android-artifacts.md),
    [playbook](playbooks/new-app-repository.md)): new library `app-shell` (the former `MainActivity`
    as `io.composecast.shell.ComposeCastActivity`, manifest, theme, default icon; per-app values are
    resources the app overrides: `composecast_server_url`, `composecast_frame_*`, `app_name`);
    included build `gradle-plugins/` with `io.composecast.app` (T-011 config code moved there; applies
    AGP, sets ids/versions/`resValue`s, placeholder manifest, icon, debug-only cleartext network
    config, non-debug https check, adds the shell: project in this repo, else
    `io.composecast:app-shell:<plugin version>`). `app-template` = `build.gradle.kts` with only the
    plugin + `compose-cast.properties`. `protocol`, `sdk-android`, `app-shell` and the plugin publish
    with `maven-publish` (group `io.composecast`, `-PcomposecastVersion`, dry run to
    `build/maven-dry-run`, Reposilite via `-PcomposecastMavenUrl` + credentials; http only for
    localhost). `distribution/android-app/` is the app repository template;
    `scripts/verify-app-repository.sh` (in `verify.sh`) builds it from the dry run. Release workflow
    job `maven` (skipped until `COMPOSECAST_MAVEN_URL` is set). Verified: demo and the template app
    installed side by side, Maestro smoke on both; local Reposilite 3.6.6 accepted every artifact.
    T-012 is next (`51aa515`).
38. **T-012 layout containers and modifiers** ([task](tasks/T-012-layout-containers.md),
    [ADR 0020](decisions/0020-component-tree-and-modifiers.md)): `Component.Column/Row/Box`
    (alignment, arrangement incl. spaceBetween/Around/Evenly, `spacing`) and a `modifier` on every
    component (`padding`, `width`/`height` dp|fill|wrap, `weight`, `background`, `cornerRadius` or
    `shape: circle`, `border`, `action`) as an unordered box model; validation with JSON paths
    (weight placement, depth 32, colors, sizes); bindings walk containers and `modifier.action`.
    Renderer: `renderChildren` + `Slot` (parent axis, scope-captured `weight`), `boxModel()` chain.
    RC alpha23 facts recorded (docs/remote-compose.md #9–12): paddings summed + inside the size,
    `CircleShape` clip dropped (oversized rounded radius instead), clickable containers merge their
    texts for accessibility, `Fit` images letterbox. Demo screen `layouts` (DSL + JSON, byte-identical),
    golden reviewed, `main` golden re-recorded (new button); emulator: Maestro `layouts.yaml` taps a
    whole card (navigate) and another (custom → snackbar). Found during a picsum.photos outage:
    images are fetched one by one at startup (15 s timeout each), so every extra image URL can delay
    the healthcheck — the demo reuses one URL; parallel prefetch is a possible follow-up. T-013 is next
    (`a444bf4`).
39. **Owner infrastructure** (2026-11-12): GHCR package `compose-cast-server` is public by inheriting the
    repository's visibility (no manual step; docs corrected). Reposilite at `https://maven.jarka.club`
    (`releases` public, redeployment off, `snapshots`/`private` private, `ci` token with `/releases rw`,
    bootstrap token removed), GitHub secrets + `COMPOSECAST_MAVEN_URL` set; `distribution/android-app`
    points at it. Playbook gained the Configuration-tab/Submit, re-`route-add` and security notes.
40. **T-013 text/image options, Icon, Divider, Card, button variants** ([task](tasks/T-013-text-image-styling.md)):
    `Component.Text` gained `color`, `fontWeight`, `fontSize`, `textAlign`, `maxLines`, `overflow`,
    `marquee`; `Component.Image` gained `contentScale`, `alpha`, `aspectRatio`; added
    `Component.Icon`, `Component.Divider`, `Component.Card` (16 dp padding, 12 dp corner radius,
    surface background) and `Component.Button` `variant` (`filled | outlined | text`) + leading
    `icon`. Validation + bindings updated across `protocol` and `sdk-server`. RC alpha23 facts
    recorded (`docs/remote-compose.md` #13–14): `Modifier.marquee` needs `velocity > 0`, `Icon`
    has no tint. Updated `layouts` cards to `ContentScale.Crop`, added `components` demo screen
    (DSL + JSON, byte-identical) and `components.png` golden screenshot (`b75647f`). T-004 is next.
41. **T-004 version negotiation** ([task](tasks/T-004-version-negotiation.md),
    [ADR 0021](decisions/0021-version-negotiation.md)): `Headers.RC_API_LEVEL` / `RC_PROFILES` and
    `UpgradeRequiredResponse` in `protocol`; `ComposeCastClient` sends `CoreDocument.DOCUMENT_API_LEVEL`
    and `RcProfiles.PROFILE_ANDROIDX` on every screen fetch and throws
    `ComposeCastUpgradeRequiredException` on HTTP 426 (`app-shell` shows `"Please update the app to
    continue."`). `RemoteComposeRenderer` and `ScreenRegistry` render at `min(client, server)` and
    cache static screens per `(id, level, profiles)`; verified in alpha23 that `DOC_DENSITY_BEHAVIOR`
    requires API level 8+, so `MIN_API_LEVEL = 8` and levels < 8 (or unsupported ops) return `426`
    with `minApiLevel` (`da06bdd`). T-014 is next.
42. **T-014 scrolling, Flow, galleries, grids** ([task](tasks/T-014-scroll-flow-gallery.md)):
    added `Modifier.scroll` (`ScrollDirection.Vertical | Horizontal`), `Component.Flow`
    (`maxItemsInEachRow`, `maxLines`), `Component.Grid` (equal-width `weight(1f)` rows with
    trailing `Spacer` padding on partial rows), and `gallery` DSL helper (`row` + horizontal
    scroll). Discovered that `LAYOUT_FLOW` (240) in RC alpha23 requires
    `PROFILE_ANDROIDX or PROFILE_EXPERIMENTAL` (`513`) in both renderer and client
    (`docs/remote-compose.md` #15). Added `collections` demo screen (DSL + JSON, byte-identical)
    and `collections.png` golden screenshot (`70e3329`). T-015 is next.
43. **T-015 adaptive layouts** ([task](tasks/T-015-adaptive-layouts.md)):
    added `Component.FitBox`, `collapsible` flag on `Row`/`Column`, `Modifier.collapsePriority`,
    `minWidth`/`maxWidth`/`minHeight`/`maxHeight` (`widthIn`/`heightIn`), and `fillFraction`
    (`fillMaxWidth(fraction)`). Verified `FitBoxLayout` and `CollapsiblePriority` rules in RC
    alpha23 (`docs/remote-compose.md` #16). Added `adaptive` demo screen (DSL + JSON,
    byte-identical) and two golden screenshots (`adaptive-phone.png` and `adaptive-tablet.png`,
    both reviewed). (`532da5c`) T-016 is next.
44. **T-016 accessibility semantics** ([task](tasks/T-016-accessibility.md)):
    added `Semantics` and `SemanticsRole` on `Modifier.semantics` (`description`, `role`,
    `stateDescription`, `hidden`, `enabled`), bindable `description`/`stateDescription` in `Binder`,
    and `ScreenDefinition.warnings()` surfaced in `/screens/{id}/debug` (clickable images/icons
    without `alt` or `semantics.description`). `RemoteComposeRenderer` emits `CoreSemantics`
    automatically for buttons (`Role.BUTTON`, `clickable = true`) and clickable containers, respects
    `hidden = true` via `Mode.CLEAR_AND_SET` and `contentDescription = null`, and records alpha23
    `AccessibleComponent.Role` values (`docs/remote-compose.md` #17) (`bd3cd2c`). T-018 is next.
45. **iOS dropped** (owner, 2026-11-16): compose-cast is Android only; ADR 0007 superseded by
    [0022](decisions/0022-android-only.md), T-005 dropped, iOS removed from the README roadmap.
46. **Project review, re-prioritization** (owner, 2026-11-16): new tasks T-025 (Milestone 1
    weather app + measurements), T-026 (image SSRF hardening), T-027 (SDK-loaded images), T-028
    (offline disk cache). Queue reordered (real apps first; T-019/T-020/T-022 `later`), MVP cut
    note in T-007, reduced scope note in T-010, risk note in ADR 0002, README fixes (modules,
    version negotiation done, Etap 12 → Later).
47. **Admin panel is the product core** (owner, 2026-11-16): T-007 MVP moved right after Milestone 1,
    new T-029 visual editor for non-developers; README vision updated.
48. **Git rule changed** (owner, 2026-11-16): agents commit verified work themselves; push only on request.
49. **Self-hosted only** (owner, 2026-11-16): everyone runs their own server and admin panel; no multi-tenant service (README principle 5, T-007 out of scope).
50. **New feature tasks and module structure** (owner, 2026-11-16): T-030…T-038 (templates, data source
    wizard, live preview, AI assistant, forms, Supabase app auth, push, analytics, feature flags/A-B),
    T-039 (split large files); [ADR 0023](decisions/0023-module-structure.md): one feature = one module
    with its own `AGENTS.md` (≤150 lines), rules R9/R10 in `check-architecture.sh`.
51. **T-026 image fetch hardening**: `HttpImageLoader` rejects hosts resolving to private addresses
    and checks every redirect hop (≤ 3); opt-out `COMPOSECAST_ALLOW_PRIVATE_IMAGE_HOSTS`
    ([ADR 0003](decisions/0003-embed-images-server-side.md) consequence).
52. **T-039 large files split** (pure refactor, documents byte-identical): RC creation code in the
    `sdk-server` package `render/` (rule R1 + [ADR 0002](decisions/0002-confine-restricted-remote-compose-api.md)
    amended to the package), `protocol` screen model in `Screen`/`ScreenValidation`/`Component`/
    `ComponentOptions`, demo screens one file each, JSON format reference → `docs/screen-json-format.md`.
54. **T-043 app bar loading title** (`ComposeCastActivity`): top bar falls back to `app_name` instead of the raw screen id while loading or on error — the "main" flash spotted in T-025 is gone.
53. **T-025 weather app prepared locally** (waits for the owner's release): `../weather-app` with 4
    JSON screens on Open-Meteo, Android app without Kotlin built against a publish dry run
    (`scripts/local-maven.init.gradle.kts`), Maestro on the emulator, measurements in the README
    (Performance); gaps → T-040…T-044; `JAVA_OPTS` documented after the heap-growth measurement.
55. **Release `v0.1.0`** (T-025): GitHub Release via `gh` (now available, AGENTS.md); server image on
    GHCR (public), Android artifacts `0.1.0` on `maven.jarka.club` after `COMPOSECAST_MAVEN_URL` moved
    from a secret to a repository variable. The weather app builds and runs on it (Maestro green).
56. **T-040 brand theme colors**: `BrandConfig.surfaceColor`, `textColor`, `outlineColor` replace the
    fixed purple-tinted defaults of card/expandable/text/divider; unset = byte-identical documents.
    New golden `brand-colors.png`. Note: `server-app` decodes `brand.json` strictly.
57. **T-041 lookups by two values**: filter `append:` and path filter arguments (`append:h.is_day`,
    `default:w.other`); the weather app's hourly list shows 🌙 at night (branch `next`, after the
    next release).
58. **Release `v0.2.0`** (T-040 brand colors, T-041 `append` + path arguments); the weather app
    moved to it (`surfaceColor`, night icons).
59. **T-042 data dir reload**: `DataDirReloader` polls `data/` every 2 s and swaps reloaded screens
    into `LiveScreens`; invalid edits keep the previous screens. `COMPOSECAST_RELOAD=false` opts out.
60. **T-044 release signing**: `app.signing.storeFile` / `keyAlias` in `compose-cast.properties`,
    passwords from env/Gradle properties only; signed `assembleRelease` without Kotlin. First unit
    tests of `gradle-plugins`, now part of `verify.sh`. All gaps from the weather app are closed.
61. **Project constitution** (`specs/`: mission, tech stack, roadmap; owner interview 2026-11-23):
    primary user = non-developer app owner; success = a non-developer ships an app without Android
    Studio, first screen in < 30 min. `specs/` is the source of truth for the plan; the old README
    Etap checklist moved to `docs/feature-checklist.md` (scripts follow). ADR 0024 (generic Preview
    app), ADR 0025 (owner's app built by GitHub Actions). Queue reordered; T-045, T-046 added;
    T-007 rewritten as steps A1–A5.
62. **T-007 A1 — `server-store` + admin mode**: SQLite via Exposed 1.6 (`screens` with draft/live,
    append-only `screen_history`, `app_settings`); `COMPOSECAST_MODE=admin` imports the data dir
    once (one transaction) and serves from the database — byte-identical to files mode.
    `7ea7fae`: the Dockerfile never copied `server-store` (image build failed); architecture rule
    R11 now checks it.
63. **T-007 A2 — login and roles**: new module `server-admin` (Ktor + Freemarker) at `/admin`;
    `users` + `sessions` in `server-store`; bcrypt, session tokens stored hashed, cookie
    `Secure; HttpOnly; SameSite=Strict` (the CSRF protection); first admin from
    `COMPOSECAST_ADMIN_EMAIL`/`_PASSWORD` (startup fails without them while there is no account);
    a viewer gets 403 on every mutating route (checked over the whole routing tree).
64. **T-007 A3 — screen list, JSON editor, drafts**: `/admin/screens` (status live/draft/
    unpublished), editor with HTMX 2.0.11 validation while typing (`ScreenJson.check`: the same
    `problems()` as file loading, with JSON paths), create screen, save draft (valid only, canonical);
    a draft never changes what apps get (tested through `GET /screens/main`).
65. **T-007 A4 — publish, history, rollback**: "Publish" makes the editor's text live without a
    restart; the registry is built with the change before anything is written (an unset secret
    → error, nothing changed); history per screen with read-only old versions; rollback = a new
    history row with the old JSON (keeps a pending draft) and restores the old bytes.
66. **T-007 A5 — app settings, admin mode in Docker (T-007 done)**: `/admin/settings` (app name,
    applicationId, server URL, brand colors with field-named errors, logo); saving rebuilds every
    screen first. Image user fixed at UID 1000 owning `/data`, production volume writable, admin
    mode refuses a read-only data dir with the `chown` fix; install playbook + `example.env` cover
    admin mode; `verify.sh --docker` logs in to admin mode.
67. **T-045 B1 — the compose-cast Preview app**: module `app-preview` (`app.preview=true` in the
    plugin: server optional, http everywhere); the shell takes the server from a
    `composecast://connect` link (alias enabled only in the Preview app) or a typed address and
    remembers it; `GET /connect` on the server is the QR target (`intent://` to the app, APK as
    fallback) — ADR 0026. Release job `preview-apk` signs it with secrets (owner sets them).
    Emulator: connect link → demo `main`; only the Preview app handles the link; branded demo app
    smoke flow still passes.
68. **T-045 B2 — draft preview**: "Preview on phone" in the editor → `__preview-<128-bit>` screen
    (in memory, 30 min, live brand, nothing saved) + an SVG QR of `/connect?screen=…`; the Preview
    app opens it on top of `main` — ADR 0027 (amends 0013). Emulator: draft visible while apps
    still get the live screen; rotation, back and a second scan with the app open work.
69. **T-029 C1 — visual editor technology** (owner): HTMX with the editor state on the server
    (the stored draft), property forms generated from the model's `SerialDescriptor`s — ADR 0028.
    T-045 B3 waits for the owner (fresh VPS, signing secrets).
70. **T-029 C2 — component tree + generated property forms**: `/admin/screens/{id}/visual` shows
    the outline and a form for the selected node, generated from the model's descriptors
    (`FormSchema`); saving checks the whole screen and stores the same draft as the JSON editor.
    Every component of `examples/demo` round-trips through its form unchanged (test); pages looked
    at with headless Chrome screenshots.
71. **T-029 C3 — palette (T-029 done)**: add (after / inside / to a list), move up/down, into the
    container above, out, wrap, duplicate, delete — `Palette` on JSON paths, each change checked
    and saved as the draft. Starters per component type (test); a repeat takes the screen's data
    source; renaming its alias renames the uses. Acceptance: the weather app's `main` and every
    demo screen are rebuilt from empty over HTTP with only editor operations, byte-identical.
72. **T-031 W1 — API probe + heuristics** (owner: T-031 before T-030): `ApiProbe` (public hosts
    only, each redirect hop checked, 2 MB like the screens), `ApiShape` (lists of objects, field kinds, role
    guesses) tested on TVMaze, Rick and Morty and JSONPlaceholder; new filter `plain` (API HTML →
    text).
73. **T-031 W2 — the wizard page**: `/admin/wizard` probes an API, offers its lists and fields with
    the guesses pre-filled, and creates a list screen (cards, max 20) and a detail screen (own
    source `…/{{params.id}}` or fields passed from the list) as drafts; raw keys are refused. Real
    TVMaze on the emulator: list with posters → tap → detail. Found on the way: `GET
    /screens/{id}?id=…` treated the query `id` as the screen id (Ktor `parameters` merges path and
    query) — every screen with a param named `id` was broken; now path parameters only. The probe
    limit is the screens' 2 MB (TVMaze `/shows` is 373 KB).
74. **T-031 W3 — field picker + data source form (T-031 done)**: data sources are tree nodes with a
    generated form (add/delete), the field picker probes the screen's sources and lists every
    `{{binding}}` with its value (inside a repeat: the item's fields) — a chip inserts it at the
    cursor (inline JS, ~15 lines). Found by the tests: saving the screen form would have dropped
    the data sources once `data` left the form. Editor docs moved to `docs/admin-editor.md`.
75. **Live dashboards concept + PoC** (owner, 2026-11-24): the owner wants beautiful live
    dashboards on RC and live edit preview, with an always-open connection instead of a refresh
    button, while static screens stay as they are. PoC (thrown away): Robolectric + emulator,
    Ktor WebSocket pushing RC **data-update documents** (`DOC_DATA_UPDATE`) — 10 updates/s, ≈ 4 KB,
    ≈ 4 ms apply, gauge animates between values. Found: named variables/arrays need `Global { }`,
    `anim()` on array elements freezes, updated texts are not re-measured
    (`docs/remote-compose.md` behaviors 18–20, "Live updates"). → [ADR 0029](decisions/0029-live-screens-websocket-data-updates.md),
    T-047 (live screens + dashboard components), T-032 moved onto the same channel, roadmap step G.
    Owner then put live first: T-047 `ready`, T-032 next, T-030 templates after them (templates
    build on live screens and dashboard components).
76. **T-047 step 1 — dashboard components**: `gauge`, `sparkline`, `kpi` (model, JSON, validation,
    DSL, `render/Dashboard.kt`, admin palette group), demo `dashboard` screen, goldens at phone and
    tablet width, checked on the emulator. Values are strings so `{{bindings}}` work unchanged
    (`NumberList`). Found: equal texts share one RC id (remote-compose.md behavior 21).
77. **T-047 step 2 — live screens on the server**: `live` on screens (JSON, DSL, admin form),
    `X-Composecast-Live`, WebSocket `/screens/{id}/live` sending the full document first and then
    RC data-update documents only when something changed (structure fingerprint decides full vs
    update), demo `live` screen with simulated metrics. Found: an update document is as large as
    the full one (state/animation kept, bytes not saved).
78. **T-047 step 3 — live screens in the app (T-047 done)**: `ComposeCastClient.liveScreen`
    (OkHttp WebSocket, backoff), `ComposeCastScreen(live = …)` applies data updates in place while
    the app is started, shell wiring, Maestro `live.yaml`. On the emulator the demo moved once a
    second for a minute; Home/Back closed the socket, returning and a server restart reconnected.
    Two bugs only the device showed (rule 5): a double connection from reading Compose state in an
    effect, and a refused connection for the previous screen after Back. Next: T-032 live preview.
79. **T-032 — live draft preview**: one preview id per admin user and screen, updated in place by
    every saved draft and served as a live screen (500 ms), so the phone follows the panel without
    a new QR code ([ADR 0030](decisions/0030-live-draft-previews.md)); a new registry is always a
    full frame; logout revokes. Preview app: "Preview ·" title, no refresh button on live screens.
    Emulator: edit → phone in 1.2 s; `scripts/verify-live-preview.sh` makes it repeatable.
80. **T-030 — app templates**: new module `app-templates` with the bundle format
    ([ADR 0031](decisions/0031-app-bundle-format.md)) and News, Shop, Events, Weather (live, with
    the T-047 dashboard components) and Blank; admin "Templates" page applies a template as drafts
    (prefix on id clashes, optional colors) and "Publish all"; an empty app opens on it. Checked on
    the emulator from an empty database with the real APIs; the device showed a too-wide key figure
    (now ellipsized) and uneven columns, both fixed. DummyJSON rejected: WebP images.
81. **T-048 spike — phone preview in the panel** (task only, no code): Robolectric + the real RC
    player rendered demo screens to PNG in 56–71 ms each (warm, heap ≈ 110 MB), the same pixels
    as the goldens, so ADR 0013's "no headless renderer" no longer holds. Limits: native graphics
    only for linux/x86_64 (no Linux arm64), and the first capture catches animations mid-way.
    Next: run Robolectric as a long-lived service outside Gradle, measured in Docker.
