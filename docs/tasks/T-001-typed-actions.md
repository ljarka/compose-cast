# T-001 — Typed actions

Status: ready · Roadmap: Etap 2 · Depends on: —

## Goal
Replace stringly-typed `"type:payload"` actions with a typed `Action` model in `protocol`, shared by
the server DSL and the Android SDK, with navigation parameters and forward compatibility.

## Why
- The JSON screen model (T-002) will store actions; their format must be fixed first.
- Navigation needs parameters for data screens (`navigate` to `movie` with `id=42`, T-003/T-006).
- README Vision principle 2: an old app must not crash on an action type it does not know.

## Verified Remote Compose facts (alpha23, recorded in docs/remote-compose.md)
- A host action's name is stored as a `TextData` UTF-8 string, read with
  `Limits.MAX_STRING_SIZE = 4000` → an encoded action longer than 4000 characters must be rejected
  when the screen is built.
- `HostNamedActionOperation` also carries an optional `valueId` (the `value` in
  `addActionCallback { name, value }`). **Decision:** parameters travel inside the name string;
  the value channel stays free for RC variables (Etap 10).

## Design
`protocol/src/main/kotlin/io/composecast/protocol/Action.kt`:
```kotlin
@Serializable
sealed interface Action {
    @Serializable @SerialName("navigate")
    data class Navigate(val screen: String, val params: Map<String, String> = emptyMap()) : Action
    @Serializable @SerialName("open_url") data class OpenUrl(val url: String) : Action
    @Serializable @SerialName("reload")   data object Reload : Action
    @Serializable @SerialName("track")
    data class Track(val event: String, val params: Map<String, String> = emptyMap()) : Action
    @Serializable @SerialName("custom")   data class Custom(val payload: String) : Action
}

object ActionCodec {
    fun encode(action: Action): String
    /** null = unknown type or malformed; never throws. */
    fun decode(raw: String): Action?
}
```
- **No `Unknown` subtype.** Every `Action` is authorable and serializable (T-002 stores them in
  JSON, where an unknown type must be an error). Forward compatibility lives in `decode` returning
  `null`; the SDK logs and ignores such actions.

### Wire grammar (the RC host-action name)
`enc(x)` = `URLEncoder.encode(x, UTF_8)` (space becomes `+`); `dec` = `URLDecoder.decode`.

| Action | Encoded | Notes |
|---|---|---|
| `Navigate(screen, params)` | `navigate:` + enc(screen) + (`?` + enc(k)=enc(v) joined by `&`, only if params) | |
| `OpenUrl(url)` | `open_url:` + url | **raw**, no query parsing, so `https://x?a=b` round-trips |
| `Reload` | `reload:` | empty payload |
| `Track(event, params)` | `track:` + enc(event) + params as in `navigate` | |
| `Custom(payload)` | `custom:` + payload | **raw**, unchanged from today's `custom:<payload>` |

- Split type from payload at the **first** `:`; split screen/event from params at the first `?`.
- Params keep **insertion order** (the encoder iterates the map as given; the decoder builds a
  `LinkedHashMap`). Rendering must stay byte-deterministic (existing test).
- Existing documents stay valid: `navigate:onboarding` → `Navigate("onboarding")`.

### Other changes
- `protocol/Actions.kt`: the constants are the wire **type names** used by the codec; add
  `RELOAD = "reload"`. Call sites outside the codec use `Action` types instead of the constants.
- `protocol/build.gradle.kts`: already exposes serialization as `api`. Create `src/test/kotlin`
  (Kotlin/JVM picks it up; `kotlin-test` is already a test dependency).
- **`sdk-android/build.gradle.kts`: `implementation(project(":protocol"))` → `api(...)`**, because
  `Action` appears in the public `ComposeCastScreen` signature.
- DSL (`sdk-server/.../ScreenDsl.kt`): `ScreenNode.Button(label, action: Action)`;
  `button(label: String, action: Action)`; keep `button(label: String, action: String)` which
  decodes and fails (`require`) on `null`; both reject encodings > 4000 chars. Delete `KNOWN_ACTIONS`.
- Renderer: `hostAction(ActionCodec.encode(node.action))`.
- SDK (`ComposeCastScreen.kt`): `onAction: (Action) -> Unit = {}`; `ActionCodec.decode(name)`;
  `null` → `Log.w` and ignore. Update KDoc.
- App (`MainActivity.kt`), exhaustive `when (action)`:
  - `Navigate` → push `action.screen`; params ignored until T-003 (`// T-003: pass params`).
  - `OpenUrl` → `Intent.ACTION_VIEW`.
  - `Reload` → re-fetch the current screen: `var reloadKey by remember { mutableIntStateOf(0) }`,
    used as `key2` of `produceState`.
  - `Track` → no-op (analytics hook). `Custom` → snackbar.
- **Demo screen for device checks**: add a screen `actions` with one button per type
  (`open_url:https://example.com`, `reload:`, `track:demo`, `custom:hello`, back to `main`), and a
  button "Actions demo" on `onboarding` that navigates to it. This intentionally changes the
  `onboarding` golden and adds an `actions` golden: add `@Test fun actions()` in
  `compat-tests/.../DemoScreensScreenshotTest.kt`, record, **look at both PNGs**, commit them.

## Steps (files to touch)
1. `protocol`: `Action.kt`, `Actions.kt`, `src/test/kotlin/io/composecast/protocol/ActionCodecTest.kt`.
2. `sdk-server`: `ScreenDsl.kt`, `RemoteComposeRenderer.kt`, `RemoteComposeRendererTest.kt`.
3. `sdk-android`: `build.gradle.kts` (`api`), `ComposeCastScreen.kt`.
4. `app-template`: `MainActivity.kt`.
5. `server-app`: `Screens.kt` (`actions` screen, button on `onboarding`; may use `Action.Navigate`).
6. `compat-tests`: `DemoScreensScreenshotTest.kt` (+ goldens).

## Acceptance criteria
- `decode(encode(a)) == a` for every type, with params containing `&`, `=`, `?`, `+`, spaces,
  `zażółć`, empty params, and `OpenUrl("https://x.dev/p?a=b&c=d")`.
- `decode` returns `null` (no exception) for `future_type:x`, `navigate`, `""`, `navigate:a?%zz`.
- `decode("navigate:onboarding") == Navigate("onboarding")`.
- `composeCast { screen("a") { button("x", action = "goto:b") } }` throws
  `IllegalArgumentException` (existing test); an encoding > 4000 chars throws too.
- `main` golden unchanged; `onboarding` changed only by the new button; `actions` golden added.
- Emulator: Get started → onboarding; Actions demo → actions; Open URL opens a browser; Reload
  re-fetches (server log shows a second `GET /screens/actions`); Custom shows a snackbar; Back pops
  each step; no `FATAL EXCEPTION`.

## Verification
`./scripts/verify.sh`; [verify-on-emulator.md](../playbooks/verify-on-emulator.md).

## Docs to update
- `protocol/AGENTS.md` — Action, codec, wire grammar, 4000-char limit; the test command now has tests.
- `sdk-android/AGENTS.md` — `onAction(Action)`, Action Routing section, `api(protocol)`.
- `sdk-server/AGENTS.md` — `button(label, Action)` usage.
- `app-template/AGENTS.md` — architecture diagram (action routing, reload).
- `compat-tests/AGENTS.md` — new `actions` test.
- README — "Defining screens" actions line, DivKit table row "Actions", Etap 2 roadmap box.
- `docs/remote-compose.md` — already has the host-action facts; re-check.
- `docs/history.md`; task queue (T-001 done, T-002 checked against the final `Action`).

## Out of scope
Using navigation params on the server (T-003); RC local actions and the value channel (Etap 10).
