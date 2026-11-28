# T-060 — Dogfood `sdk-android` in `app-shell`

Status: done · Roadmap: H3 · Depends on: T-059 · Module: app-shell, sdk-android

## Goal

Refactor `app-shell` (`ComposeCastActivity.kt`) so that `app-template` and `app-preview` run on top
of the public `ComposeCastFlow` and `ComposeCastNavigator` APIs from `sdk-android` instead of
maintaining private copies of fetching, live-screen wiring, back-stack state, and action routing.

## Why

[ADR 0035](../decisions/0035-android-sdk-public-api.md): `app-shell` must be a thin Material 3 +
Activity + Preview wrapper over `sdk-android`. Dogfooding guarantees that `app-template`,
`app-preview`, and external SDK consumers share one tested code path.

## Design

- Replace `app-shell`'s private `BackStackSaver`, `produceState`, live-screen `remember`, and
  `ComposeCastScreen` call with `rememberComposeCastNavigator` + `ComposeCastFlow` from
  `sdk-android`.
- Keep in `app-shell`:
  - `ComposeCastActivity` (reading `resValue` config, `enableEdgeToEdge`, `PreviewLink` / `onNewIntent`).
  - `homeScreen` resolution from `client.getBrand().homeScreen` (and refresh when reloading root).
  - `OpenRequest` (draft preview deep link pushing onto `navigator.navigate(request.screen)`).
  - Material 3 `Scaffold`, `TopAppBar` (title from `ComposeCastUiState.Ready`, "Preview · …" prefix,
    refresh button hidden when `liveEveryMs != null`, settings gear in preview mode), `SnackbarHost`,
    and Material 3 `loading` / `error` slots passed into `ComposeCastFlow`.
  - `ConnectScreen` and `PreviewSettingsScreen`.
- If refactoring reveals any ergonomics gap in `ComposeCastFlow` / `ComposeCastNavigator`, fix it in
  `sdk-android` so external developers benefit too.

## Steps (files to touch)

1. `app-shell/src/main/kotlin/io/composecast/shell/ComposeCastActivity.kt`: migrate `ComposeCastApp`
   to `ComposeCastFlow` + `ComposeCastNavigator`.
2. `sdk-android/...` (only if small API adjustments are needed for clean dogfooding).
3. Update `app-shell/AGENTS.md` to reflect the simplified architecture.

## Acceptance criteria

- `app-shell` has no private `BackStackSaver`, no direct `client.getScreen` / `client.liveScreen`
  state machine, and no direct `ComposeCastScreen` call — everything goes through `ComposeCastFlow`.
- Behavior of `app-template` and `app-preview` is unchanged (including draft preview links, live
  dashboards, rotation state restoration, refresh button hiding on live screens, and error states).
- `./scripts/verify.sh` is green.
- Verified on emulator with `maestro test app-template/maestro/smoke.yaml` and
  `maestro test app-preview/maestro/connect.yaml`.

## Verification

- `./scripts/verify.sh`
- Emulator check: `./gradlew :app-template:installDebug` + `maestro test app-template/maestro/smoke.yaml`
- Emulator check: `./gradlew :app-preview:installDebug` + `maestro test app-preview/maestro/connect.yaml`

## Docs to update

- `app-shell/AGENTS.md`
- `docs/history.md`

## Out of scope

- New shell features or UI changes.
- External consumer sample app (T-061).
