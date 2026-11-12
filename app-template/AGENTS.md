# app-template — AGENTS.md

The demo Android app, built **exactly like an app repository** (T-024): `build.gradle.kts` applies
only `io.composecast.app`, everything else is `compose-cast.properties`. No Kotlin, manifest or
resources — the app itself is [`app-shell`](../app-shell/AGENTS.md), configured by the
[plugin](../gradle-plugins/AGENTS.md). The template for real app repositories is
`distribution/android-app/` ([playbook](../docs/playbooks/new-app-repository.md)).

## Files

| File | Purpose |
|---|---|
| `build.gradle.kts` | `plugins { id("io.composecast.app") }` (from the included build `gradle-plugins`) |
| `compose-cast.properties` | Demo config: name, `io.composecast.demo`, `http://10.0.2.2:8080`, frame colors (keys: gradle-plugins/AGENTS.md) |
| `maestro/smoke.yaml` | Maestro UI flow: launch, tap "Get started", back (`-e APP_ID=<id>` for other configs) |

Do NOT add code here to prove SDK behavior — use `compat-tests`; app behavior belongs in `app-shell`.

## Build

```bash
./gradlew :app-template:installDebug                                        # demo app
./gradlew :app-template:installDebug -Pcomposecast.appConfig=<path>         # another app, side by side
./gradlew :app-template:assembleRelease                                     # fails: http server URL (by design)
maestro test app-template/maestro/smoke.yaml                                # click through (server running)
```

APK: `app-template/build/outputs/apk/debug/app-template-debug.apk`. Activity:
`io.composecast.demo/io.composecast.shell.ComposeCastActivity`.
