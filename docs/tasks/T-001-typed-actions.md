# T-001 — Typed actions

Status: ready · Roadmap: Etap 2 · Depends on: —

## Goal
Replace stringly-typed `"type:payload"` actions with a typed `Action` model in `protocol`, shared by
the server DSL and the Android SDK, with navigation parameters and forward compatibility.

## Why
- The JSON screen model (T-002) will store actions; its format must be fixed first.
- Navigation needs parameters for data screens (`navigate` to `movie` with `id=42`, T-003/T-006).
- Principle 2 (README Vision): an old app must not crash on an action type it does not know.

## Design
- `protocol/src/main/kotlin/io/composecast/protocol/Action.kt`:
  ```kotlin
  @Serializable sealed interface Action {
      @Serializable @SerialName("navigate") data class Navigate(val screen: String, val params: Map<String, String> = emptyMap()) : Action
      @Serializable @SerialName("open_url") data class OpenUrl(val url: String) : Action
      @Serializable @SerialName("reload")   data object Reload : Action
      @Serializable @SerialName("track")    data class Track(val event: String, val params: Map<String, String> = emptyMap()) : Action
      @Serializable @SerialName("custom")   data class Custom(val name: String, val payload: String = "") : Action
      /** Decoded from a type this client does not know; hosts ignore it. Never authored. */
      data class Unknown(val raw: String) : Action
  }
  object ActionCodec { fun encode(a: Action): String; fun decode(s: String): Action }
  ```
- Wire encoding (the RC host-action name, a string): keep `type:payload` so existing documents stay
  valid. `navigate:movie?id=42&lang=pl`, `open_url:https://…`, `reload:`, `track:event?k=v`,
  `custom:name?payload=…`. URL-encode keys/values (`java.net.URLEncoder`/`URLDecoder`, UTF-8).
  Unknown type or malformed payload → `Action.Unknown(raw)`, never an exception.
- `Actions` constants stay as the type names (used by the codec); remove them from call sites.
- DSL (`sdk-server/.../ScreenDsl.kt`): `button(label: String, action: Action)`. Keep
  `button(label, action: String)` as a convenience that decodes and **rejects** `Unknown` at
  build time (existing `Screens.kt`/README keep working). `ScreenNode.Button` holds `Action`;
  the renderer emits `hostAction(ActionCodec.encode(action))`.
- SDK (`sdk-android/.../ComposeCastScreen.kt`): `onAction: (Action) -> Unit`, decoding with
  `ActionCodec.decode(name)`.
- App (`app-template/.../MainActivity.kt`): `when (action)` over `Navigate` (push `action.screen`;
  params are ignored until T-003 — leave a `// T-003` comment), `OpenUrl`, `Reload` (re-fetch the
  current screen), `Track` (no-op), `Custom` (snackbar), `Unknown` (ignore, log).

## Steps (files to touch)
1. `protocol`: `Action.kt` + `ActionCodec`; tests in `protocol/src/test/kotlin/.../ActionCodecTest.kt`
   (protocol has `kotlin-test`; add the test source set if missing).
2. `sdk-server`: `ScreenDsl.kt`, `RemoteComposeRenderer.kt`, `RemoteComposeRendererTest.kt`.
3. `sdk-android`: `ComposeCastScreen.kt` (+ KDoc).
4. `app-template`: `MainActivity.kt`.
5. `server-app/Screens.kt`: may stay on the string overload; optionally switch to `Action.Navigate`.

## Acceptance criteria
- Round trip `decode(encode(a)) == a` for every authored type, including params with `&`, `=`,
  `?`, spaces, non-ASCII (`zażółć`), empty params.
- `decode("future_type:x")` → `Unknown`; `decode("navigate")`, `decode("")` → `Unknown`, no throw.
- Existing documents' action strings (`navigate:onboarding`) decode to `Navigate("onboarding")`.
- `button("x", action = "goto:b")` still fails at server start (existing test).
- Screenshots unchanged (actions are invisible); on the emulator "Get started" → onboarding,
  "Continue" → main, Back pops.

## Verification
`./scripts/verify.sh`; [verify-on-emulator.md](../playbooks/verify-on-emulator.md) (tap both buttons, back).

## Docs to update
`protocol/AGENTS.md` (Action, codec, wire format), `sdk-android/AGENTS.md` (onAction signature,
action routing section), `sdk-server/AGENTS.md`, README "Defining screens" (actions line),
`docs/history.md`; this task → done; check T-002 still matches.

## Out of scope
Using navigation params on the server (T-003); RC local actions (Etap 10).
