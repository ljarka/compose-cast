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
