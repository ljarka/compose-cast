# Playbook — upgrade Remote Compose

RC is alpha; semantics change between versions (alpha20 → alpha23 changed density scaling and
would have doubled sizes). Read first: [docs/remote-compose.md](../remote-compose.md),
[ADR 0003](../decisions/0003-embed-images-server-side.md), [ADR 0004](../decisions/0004-units-dp-and-sp.md).
Upgrade RC **alone**, never together with other dependencies.

1. **Latest version and what changed**: `./scripts/check-rc-release.sh` (also runs weekly in CI:
   a failed "Remote Compose release watch" run = new release). Read the
   [release notes](https://developer.android.com/jetpack/androidx/releases/compose-remote) for
   every version between ours and the new one; note API/behavior changes to re-check in step 3.
2. **Bump** `remote-compose` in `gradle/libs.versions.toml`; `./scripts/verify.sh --quick`.
3. **Re-check every known behavior in the new sources** (download as in docs/remote-compose.md, in a
   scratch dir):
   ```bash
   grep -rn 'ENABLE_IMAGE_URLS =' remote-core                          # ADR 0003
   grep -rn 'CHECK_DATA_SIZE =' remote-player-core                     # ADR 0003
   grep -rn -A2 'addBitmapUrl(@NonNull String url) {' remote-creation-core   # still 1x1?
   grep -rn -A6 'class HeightRcFloatModifier' remote-creation-core     # written() still needed?
   grep -rln 'getDensityBehavior' remote-core                          # ADR 0004 table
   grep -n 'DOCUMENT_API_LEVEL =' remote-core/androidx/compose/remote/core/CoreDocument.java
   grep -c RestrictTo remote-creation-core/androidx/compose/remote/creation/dsl/RcDocCreator.kt
   ```
   For every file in the density list, read how DP mode treats constants vs expressions.
4. **Screenshots** — `./gradlew :compat-tests:verifyRoborazziDebug`. On a diff, open
   `compat-tests/build/outputs/roborazzi/*_compare.png` and decide: did RC content change, or only the
   Material3 frame? Fix the renderer; re-record only for intended changes.
5. **Device** — [verify-on-emulator.md](verify-on-emulator.md).
6. **Docs** — ADR 0004 table (new column), `docs/remote-compose.md` (current version, re-verified
   facts), `docs/history.md`, version in root `AGENTS.md` and README status line.
7. `./scripts/verify.sh`.
