# compose-cast — Project State

Remote: git@github.com:ljarka/compose-cast.git
SSH key: ~/.ssh/id_ed25519

## Architecture
Server → `RemoteComposeWriter` → binary `.rc` → Android `RemoteComposePlayer`
**NOT JSON.** JSON-based ScreenModel approaches are wrong.

## Progress

### Etap 0 — Spike techniczny
- [ ] Verify remote-creation-jvm works on plain JVM
- [ ] Generate Hello .rc document
- [ ] Display from assets in Android app
- [ ] Named colors API check
- [ ] .kts script host smoke test

### Etap 1 — Repozytorium i CI
- [x] 2026-11-07: Initial Gradle multi-module structure (commit a63271a)
  - 7 modules: protocol, sdk-server, server-scripting, server-app, sdk-android, app-template, compat-tests
  - CI: .github/workflows/ci.yml
- [ ] Verify CI passes on GitHub Actions
- [ ] Add ktlint config and badge to README

### Etap 2–8
See /root/.openclaw/workspace/projects/compose-cast/state.md for full plan.

## Next task
Complete Etap 0 spike: verify `remote-creation-jvm` works on plain JVM, generate a minimal `.rc` document.

Steps:
1. Add dependency `androidx.constraintlayout:constraintlayout-compose-android` or `remote-creation-jvm` to a spike module
2. Write a Kotlin main() that uses RemoteComposeWriter to create a minimal document with a Text component
3. Write it to a file `hello.rc`
4. Verify it compiles and runs (`./gradlew :spike:run` or similar)
5. Document findings in docs/history.md

Ref: https://github.com/androidx/constraintlayout/tree/main/projects/remotecompose

## Blockers
None currently.
