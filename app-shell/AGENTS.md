# app-shell — AGENTS.md

The app every compose-cast Android app runs (T-024, [ADR 0018](../docs/decisions/0018-distribution-like-immich.md)):
one activity that fetches the server's screens and plays them, with navigation/back stack,
loading/error states and action routing. Published as `io.composecast:app-shell`; apps get it
through the `io.composecast.app` Gradle plugin ([gradle-plugins](../gradle-plugins/AGENTS.md)) and
contain no Kotlin. For SDK details read `sdk-android/AGENTS.md`.

## Files

| File | Purpose |
|---|---|
| `ComposeCastActivity.kt` | Launcher activity; reads config from resources; `ComposeCastApp` composable (back stack, states, action routing, live screens: `client.liveScreen` passed to `ComposeCastScreen` when the document is live **and** its id matches the back-stack entry — for one frame after Back the previous document is still shown), frame color scheme, status bar style |
| `PreviewConnection.kt` | Preview app (T-045): `PreviewLink.parse` (`composecast://connect?url=…[&screen=…]`), `PreviewLink.normalize` (typed address; no scheme = https), `PreviewServerStore` (SharedPreferences) |
| `ConnectScreen.kt` | Preview app without a server: how to connect + address field |
| `AndroidManifest.xml` | `INTERNET`, application label/icon/theme/`networkSecurityConfig`, the launcher activity (`singleTop`), the `ConnectLink` alias (`composecast://connect`, enabled only by `@bool/composecast_preview`) — merged into every app |
| `res/values/composecast_config.xml` | **Defaults** of the values the plugin overrides: `app_name`, `composecast_server_url`, `composecast_frame_primary`, `composecast_frame_background`, `composecast_preview` (bool) |
| `res/values/themes.xml` | `Theme.ComposeCast`: window/splash background = `composecast_frame_background` |
| `res/xml/composecast_network_security_config.xml` | https only; the plugin overrides it in debug builds (cleartext to 10.0.2.2/localhost/127.0.0.1) |
| `res/mipmap-anydpi/ic_launcher.xml`, `res/drawable/ic_launcher_foreground.xml` | Default adaptive icon (cast glyph on `composecast_frame_primary`); overridden by `app.icon` |

## Configuration contract (with the plugin)

The shell never reads `BuildConfig` (a library has none for the app): every per-app value is an
**app resource that overrides the library default** (`resValue` in the app module wins over library
resources). Renaming one of these resources is a breaking change for the plugin — change both in
the same commit and keep the plugin and shell versions equal (the plugin adds the shell in its own
version).

## Architecture

```
ComposeCastActivity (config: getString/getColor of the resources above)
 └── ComposeCastApp (Composable)
      ├── backStack: List<Action.Navigate> (rememberSaveable, BackStackSaver) + BackHandler; entry = last
      ├── produceState(entry, reloadKey) → ComposeCastClient.getScreen(entry.screen, entry.params)
      ├── loading  → CircularProgressIndicator
      ├── error    → ComposeCastHttpException: "Server error <status>"; else "Cannot reach <url>"; Retry (reloadKey++)
      └── success  → ComposeCastScreen(screen, onAction)   (Remote Compose player)
```

- Back stack: `Action.Navigate(screen, params)` is pushed as is (params → query of
  `GET /screens/{screen}`); system back pops; saved as `ActionCodec` wire strings → survives rotation.
- Action routing: exhaustive `when` over `Action` — `Navigate` push, `OpenUrl` `ACTION_VIEW` (no app
  → snackbar), `Reload` `reloadKey++`, `Track` no-op (analytics hook), `Custom` snackbar. Unknown
  actions never arrive (`sdk-android` drops them).
- Top app bar title: the screen's `title` from the document, or `app_name` while loading/on error (never the raw screen id). T-043.
- Frame colors: top app bar = primary (title black/white by luminance), loading/error background =
  background; status bar icons light/dark by the primary's luminance (`enableEdgeToEdge`). Content
  colors are the server's brand, painted by the RC document.
- Server URL is state: `app.serverUrl`, or in the Preview app the linked/typed one (null → `ConnectScreen`).
  `key(url)` gives each server a new client and back stack; "Change server" (Preview only) forgets
  it. A connect link while running arrives in `onNewIntent` (`singleTop`). A link's `screen`
  (draft preview, B2) is pushed on top of `main` via `OpenRequest` (seq: each scan counts; not again
  after a rotation).
- Config is `lazy`: resources are not available before `onCreate`.

## Build

```bash
./gradlew :app-shell:assembleDebug :app-shell:lintDebug :app-shell:testDebugUnitTest   # PreviewLinkTest
./gradlew :app-template:installDebug     # the shell runs in the demo app
```

## Constraints

- `minSdk = 26` (`remote-player-view`, [ADR 0005](../docs/decisions/0005-player-view-wrapper.md));
  no RC imports — only `sdk-android` public API ([ADR 0002](../docs/decisions/0002-confine-restricted-remote-compose-api.md)).
- Activity class `io.composecast.shell.ComposeCastActivity` is part of the contract (adb/Maestro,
  deep links later): `adb shell am start -n <applicationId>/io.composecast.shell.ComposeCastActivity`.
