# app-preview — AGENTS.md

The generic **compose-cast Preview** app (T-045, [ADR 0024](../docs/decisions/0024-generic-preview-app.md),
[ADR 0026](../docs/decisions/0026-preview-connect-page.md)): shows any compose-cast server, chosen at
runtime. Built exactly like an app repository: the plugin plus `compose-cast.properties` with
`app.preview=true` — no Kotlin here; the behavior is in [`app-shell`](../app-shell/AGENTS.md)
(`PreviewConnection.kt`, `ConnectScreen.kt`). Playbook: [preview-app.md](../docs/playbooks/preview-app.md).

## Files

| File | Purpose |
|---|---|
| `build.gradle.kts` | `plugins { id("io.composecast.app") }` |
| `compose-cast.properties` | `io.composecast.preview`, name "compose-cast Preview", `app.preview=true` (no `app.serverUrl`), neutral frame colors |
| `maestro/connect.yaml` | Change server → connect screen → server's `/connect` in Chrome → "Open in compose-cast Preview" → `main` (`-e SERVER=…` required) |

## Decisions

- A separate module instead of a flavor of `app-template` (T-045 said "flavor"): own build output,
  no `-Pcomposecast.appConfig` switching, and it is literally what an app repository looks like.
- `app.preview=true` (plugin): server URL optional, plain HTTP to any host in **every** build (the
  owner's server is often http during setup), `@bool/composecast_preview` enables the
  `composecast://connect` link and the connect screen in the shell. Never set it in a branded app.
- The applicationId `io.composecast.preview` is part of the contract: `server-app`'s
  `ConnectPage.PREVIEW_PACKAGE` puts it into the `intent://` link.
- Release APK: built and signed only by the release workflow (job `preview-apk`, secrets in the
  playbook); `versionName` = tag, `versionCode` = run number.

## Build

```bash
./gradlew :app-preview:installDebug
./gradlew :app-preview:assembleRelease     # unsigned locally; must build (verify.sh)
```
