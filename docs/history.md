# History

What was done, in order, and where the reasoning lives. Numbers are the order of work — not the
README roadmap's "Etap" numbers.

1. **Initial state** (up to `2f2103c`): JSON `ScreenModel` + hand-written renderer, RC unused,
   Android never compiled → [ADR 0001](decisions/0001-remote-compose-wire-format.md).
2. **Server emits RC** (`99d8979`): `RemoteComposeRenderer`, binary `/screens/{id}`, `/debug`,
   build-time validation. `c39bb91`: debug-only cleartext HTTP for the emulator (targetSdk ≥ 28
   blocks cleartext).
3. **App plays RC** (`e47cdb2`): `sdk-android` on `RemoteComposePlayer`, JSON renderer deleted,
   `expectSuccess = true`, back stack. Embedded images, units, `height` workaround
   ([0003](decisions/0003-embed-images-server-side.md), [0004](decisions/0004-units-dp-and-sp.md)).
4. **Handoff docs** (`5ac3534`).
5. **Cleanup** (`ab79b5f`): legacy JSON models removed, Android build + lint in CI.
6. **AGP 9.5.1 / Gradle 9.6.0** (`cd9029d`): built-in Kotlin, no `kotlin-android` plugin.
7. **Screenshot tests** (`e8dd8f1`): `compat-tests` → [ADR 0006](decisions/0006-screenshot-tests-as-goldens.md).
8. **Dependency refresh** (`f31415c`): Compose BOM, Kotlin 2.4.21, Ktor 3.6.1, compileSdk 37 /
   targetSdk 36. Material3 TopAppBar shorter under Robolectric; RC content pixel-identical.
9. **Vision & roadmap** (`9e67e84`): DivKit-style framework on RC.
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
14. **Roadmap expansion** (`907ed06`, `c6288a9`): full Etap 0–12 roadmap in README (DivKit mapping
    table, two app milestones, admin panel, iOS, data sources); Etap 12 Experimentation (A/B
    testing, feature flags, gradual rollout) added; admin panel governance (change history,
    rollback, screen variants) added to Etap 8.
15. **RC knowledge gap fixed**: `fontSize()` = 14sp assumption documented in
    `docs/remote-compose.md` (item 7) — silent failure risk on RC upgrades.
    Admin panel task stub added (`docs/tasks/T-007-admin-panel.md`).
17. **T-008 local state spike added**: official RC docs (`developer.android.com`) confirmed
    `RemoteStateLayout`, `RemoteCollapsibleRow/Column`, `rememberMutableRemoteInt` exist.
    Unknown whether these are accessible from procedural `remote-creation-core` API (JVM).
    T-008 spike task added to investigate. T-007 admin panel explicitly scoped to static
    documents until T-008 resolves. Official component catalog added to
    `docs/remote-compose.md`.
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
