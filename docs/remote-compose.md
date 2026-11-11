# Remote Compose — verified knowledge

Facts about `androidx.compose.remote` that compose-cast depends on, **with the version they were
verified on**. Update this file whenever you learn or re-verify something; on an RC upgrade go
through every section.

Current version: **1.0.0-alpha23** (catalog: `remote-compose` in `gradle/libs.versions.toml`).

---

## What It Is

Remote Compose is an AndroidX / Jetpack library that lets you write UI with Compose-style APIs but
**execute and render that UI in a different process**. The app creates a serialized UI document; a
**player** in a host process receives and renders it locally without calling back into the originating
app. Primary use cases designed by Google: App Widgets, Wear OS widgets.
compose-cast repurposes this for HTTP server-driven UI over a network.

### Three Stages

1. **Create** — `@RemoteComposable` functions run and record a compact, serialized UI document.
2. **Transport** — The document is passed to a host process (via IPC, HTTP, or any channel).
3. **Play** — A Remote Compose **player** in the host reads the document and handles layout, input,
   state, and animations locally — no calls back to the originating app.

---

## Official Higher-Level API (`@RemoteComposable`)

The official docs describe a **Compose DSL approach** using `@RemoteComposable` annotated functions
and a component library. This is exposed through the `remote-creation-compose` artifact (unused by
compose-cast today — requires Compose runtime at creation time).

### Why compose-cast uses the lower-level procedural API instead

**Verified (alpha23 POM + Gradle module metadata):** `remote-creation-compose` (`RemoteText`,
`RemoteBox`, `rememberMutableRemoteInt`, …) is published **only as an Android AAR** (single
`release` variant, no JVM/desktop variant) and depends on Android Compose `ui`, `foundation`,
`runtime`, `core-ktx`, `lifecycle-viewmodel`. It cannot run in the Ktor JVM server; using it would
mean rendering documents on an Android runtime (device, emulator, Robolectric/layoutlib in the
server) — not worth it, because the procedural DSL covers the same document operations, including
state (section below). compose-cast uses `remote-creation-core` + `remote-creation-jvm`
for server-side document creation — pure JVM, no Compose runtime dependency.

If Compose Multiplatform support for `remote-creation-compose` is confirmed in a future RC release,
evaluate migrating to the higher-level DSL (task: spike in Etap 1 or Etap 2 upgrade).

### State API — procedural (verified in `remote-creation-core` 1.0.0-alpha23 sources)

The state machinery of the official API **is available in the procedural `RcScope` DSL we already
use on the JVM** — no Compose runtime needed (answers T-008 questions 1–4):

| Need | Procedural API (`androidx.compose.remote.creation.dsl`) |
|---|---|
| Mutable state | `remoteInteger(v)`, `remoteNamedInteger(name, v)` (named = host can set it), `remoteBool(v)`, `remoteLong(v)` → `RcInteger` / `RcBool` (`RcScope.kt` ~l.531, 1362) |
| Change state on tap | `Modifier.onClick { setValue(variable, value or expression); hostAction(…) }` — local `ValueIntegerChange` ops and host actions can be combined in one click (`RcActionScope.kt`) |
| Branch on state | `StateLayout(stateIndex: RcInteger) { child0; child1; … }` (`RcScope.kt` l.53) |
| Adaptive layout | `CollapsibleColumn` / `CollapsibleRow` + `Modifier.vertical/horizontalCollapsiblePriority(p)` |
| Other | `runAction { }`, `impulse(…)`, `skip(…)` (conditional segments), `timeAttribute(…)` |

**Verified player behavior (T-008 PoC, alpha23 `StateLayout.java` + `ValueIntegerChangeActionOperation.java`):**
- Every direct child of `StateLayout` **must** be a `LayoutManager` (`Column`, `Row`, `Box`, …);
  `getLayout(idx)` throws `RuntimeException("Child is not a LayoutManager")` otherwise.
- Non-selected children have `mVisibility = Visibility.GONE` (`inflate()` calls
  `hideLayoutsOtherThan(currentLayoutIndex)`). `measure()`, `computeWrapSize()`, `layout()` and
  `onClick()` only delegate to `getLayout(currentLayoutIndex)` — collapsed/hidden branches take
  zero space and do not receive clicks.
- `StateLayout` does **not** implement `VariableSupport`: it reads
  `mRemoteComposeState.getInteger(mIndexId)` only inside `paint()`. On the first measure/layout/paint
  pass `currentLayoutIndex` and `measuredLayoutIndex` are `0` regardless of the initial integer
  value. Always initialize `remoteInteger(0)` and emit the initial state as child `0` and the
  toggled state as child `1` so frame 1 renders without a flash or second pass.
- `StateLayout.paint()` overrides `Component.paint()` without calling `super.paint()` /
  `paintingComponent()`, so: (a) it does **not** run `ComponentData` operations (`IntegerConstant`,
  `IntegerExpression`) placed directly inside `StateLayout` — declare `remoteInteger(0)` in the
  enclosing layout before `StateLayout`; and (b) it forgets `context.translate(mX, mY)`, painting at
  `(0, 0)` of its parent — always wrap `StateLayout` in a `Box(Modifier.fillMaxWidth())` so the `Box`
  carries the parent offset and `StateLayout` sits at `(0, 0)` inside the `Box`.
- `setValue(state, next)` emits `ValueIntegerChangeActionOperation(id, next)`, whose `runAction`
  calls `context.overrideInteger(id, next)` (`mIntegerOverride[id] = true`, so later
  `IntegerConstant.apply` calls on repaint do not reset it) and `RemoteComposeView.performClick()`
  calls `invalidate()`, triggering `StateLayout.paint()` → `invalidateMeasure()` locally on the
  device with zero host callbacks or network requests.

Also present but `@RestrictTo`: `creation/json/RemoteComposeJsonParser` — a low-level
JSON form of RC operations (resources, expressions, modifiers); not a replacement for the semantic
`ScreenDefinition` model ([ADR 0015](decisions/0015-screen-model-is-the-product.md)).

### Component catalog (from official docs, alpha23)

| Component | Type | Notes |
|---|---|---|
| `RemoteBox`, `RemoteRow`, `RemoteColumn` | Basic layout | Standard containers |
| `RemoteFitBox`, `RemoteCollapsibleRow`, `RemoteCollapsibleColumn` | Adaptive layout | Handle dynamic screen sizes |
| `RemoteMaterialTheme` | Theming | Color + typography for Wear OS |
| `RemoteCanvas` | Custom drawing | Records draw commands; runs on player host |
| `RemoteText` | Text | Accepts `RemoteString` for dynamic text |

### Procedural catalog — everything `RcScope` offers (verified in alpha23 sources, 2026-11-07)

Source: `remote-creation-core` `dsl/RcScope.kt`, `dsl/Modifier.kt`, `dsl/RcTypes.kt`; layout
managers in `remote-core/operations/layout/managers`, modifiers in `.../layout/modifiers`.
compose-cast tasks that expose each group: T-012…T-021 ([queue](tasks/README.md)).

| Group | API | Task |
|---|---|---|
| Containers | `Box(h, v)`, `Column(h, vArrangement)`, `Row(hArrangement, v)`, `Spacer` | T-012 |
| Flow | `Flow(h, v, maxItemsInEachRow, maxLines)` (wrapping row; `weight` in scope) | T-014 |
| Adaptive | `FitBox`, `CollapsibleRow/Column` + `horizontal/verticalCollapsiblePriority` | T-015 |
| State | `StateLayout(stateIndex)`, `remoteInteger/Bool/Float/NamedX`, `Modifier.visibility(RcInteger)`, `animationSpec`, `defineVisibilityAnimation` | done (`expandable`, T-008) / T-017 |
| Text | `Text(text \| RcText, color, fontSize, fontWeight, textAlign, overflow: Clip/Visible/Ellipsis/StartEllipsis/MiddleEllipsis, maxLines)`, `remoteTextStyle`, `Modifier.marquee`, `alignByBaseline` | T-013 |
| Image | `Image(image, contentDescription, contentScale: Fit/Crop/FillBounds/FillWidth/FillHeight/Inside/None, alpha)`, `Icon` (= Image, Fit) | T-013 |
| Size / layout modifiers | `padding`, `size`, `width/height` (Float, RcFloat, dp), `fillMaxWidth/Height/Size(fraction)`, `fillParentMax*`, `wrapContent*`, `widthIn/heightIn`, `requiredWidthIn/HeightIn`, `weight` (Row/Column/Flow scopes), `offset`, `zIndex` | T-012, T-015, T-019 |
| Decoration | `background(color \| RcColor)`, `clip(Shape)` (`RectShape`, `RoundedRectShape`, `CircleShape`), `border(width, corner, color, RcBorderShape: Rectangle/Circle/RoundedRectangle)`, `dynamicBorder` | T-012, T-013 |
| Scroll | `verticalScroll(position)`, `horizontalScroll()` | T-014 |
| Input | `onClick`, `onLongClick`, `onDoubleClick`, `onTouchDown/Up/Cancel`, `ripple()`, `performHaptic(RcHaptic)`, `addTouch` | T-019 |
| Actions in a click | `hostAction(name)`, `setValue(var, value \| expression)`, `runAction` | done / T-017 |
| Effects | `graphicsLayer { scaleX/Y, alpha, rotationZ, translationX/Y }`, `drawWithContent` | T-019 |
| Accessibility | `semantics { contentDescription, role, text, stateDescription, enabled, clickable }` | T-016 |
| Canvas | `Canvas { drawRect/Circle/Line/Arc/Sector/Oval/RoundRect/Path/TweenPath/TextAnchored/TextOnPath/TextOnCircle/Bitmap/ScaledBitmap }`, paint, shaders (gradients), paths (`remotePath`, `remoteXYPath`, `remotePolarPath`), `save/restore/scale/skew` | T-018 |
| Dynamic values | time (`hour()`, `minutes()`, `dayOfMonth()`, `timeAttribute`), `createTextFromFloat`/`format`, `textMerge`, `textLookup`, `windowWidth/Height`, `apiLevel`, `ifTrue`/`conditionalOperations`, `remoteThemedColor(light, dark)`, named colors/floats/texts (host-settable) | T-020 |
| Native views | `Custom(config, properties)` layout; Android player hosts it via `RemoteComposePlayer.setCustomSupport(AndroidCustomContext)` (`remote-player-view`) | T-021 |
| Exotic | sensors (accelerometer, gyroscope, magnetometer, light), particles, sound (`addSound`, `playSound`), meshes, bitmap fonts, `defineMacro` (templates), `wakeIn` | T-022 |

**There is no text-input operation** in alpha23 (no text field in `Rc.java`/`RcTypes.kt`; only
keyboard haptics) — text input/search needs a native view through `Custom` (T-021) or a
host-side overlay. No lazy lists: `for` loops run at creation time, which is fine for us because
documents are rendered per request on the server.

### State API (symbolic references, not in-memory values)

```kotlin
// App code cannot read these values back — the player manages them on the host
val count = rememberMutableRemoteInt(0)
val color = rememberNamedRemoteColor("primary")
val label = rememberMutableRemoteString("")
```

### Actions and dynamic branching

```kotlin
// Declarative actions (instead of lambdas)
RemoteModifier.clickable(action)        // attach action to any element
RemoteModifier.background(remoteColor)  // dynamic styling from remote state

// Action types
valueChange(stateRef, newValue)         // update remote state on host
pendingIntentAction(pendingIntent)      // hand off to app/host
hostAction("name")                      // named callback → addActionCallback on player
combinedAction(action1, action2)        // sequence of actions

// Dynamic UI branching (instead of Kotlin if/when over remote state)
RemoteStateLayout(state) {              // maps state values to different UI slots
    on(0) { /* branch A */ }
    on(1) { /* branch B */ }
}
```

### Remote system context (use on host, not local app environment)

```kotlin
// Don't use LocalConfiguration / LocalDensity — they reflect app environment at recording time
// Use instead:
RemoteConfiguration   // resolves on host at runtime
RemoteDensity         // resolves on host at runtime
```

### Standard Compose vs Remote Compose comparison (official)

| Feature | Standard Compose | Remote Compose |
|---|---|---|
| Click events | In-process lambdas | Declarative `Action` objects |
| Conditional logic | Kotlin `if`/`when` over live state | `if`/`when` works for static state; dynamic → `RemoteStateLayout` |
| Loops | Standard `for`/`while` | `for`/`forEach` only over data known at record time |
| Execution host | App process | Launcher (AppWidget), Wear OS, or any player |
| State | Local memory values | Symbolic references (`rememberMutableRemoteInt()`) |
| Side effects | `LaunchedEffect` coroutines | Not available on host |

---

## Artifacts we use

| Artifact | Used in | Notes |
|---|---|---|
| `remote-creation-core`, `remote-creation-jvm` | `sdk-server` | JVM document creation (`createRcBuffer`, `RcScope`) |
| `remote-core` | `sdk-server`, `sdk-android` | format, `CoreDocument`, reader (`describe()`) |
| `remote-player-core`, `remote-player-view` | `sdk-android` | `RemoteComposePlayer` (View); minSdk 26 |

Also published (unused): `remote-player-compose`, `remote-creation-compose`,
`remote-creation-android`, `remote-creation-expression`, `remote-testing`, `remote-tooling-preview`.

**No iOS / Kotlin Multiplatform artifacts** (checked 2026-11-07) → [ADR 0007](decisions/0007-ios-strategy-spike.md).

---

## How to research the API (do this instead of guessing)

```bash
V=1.0.0-alpha23; A=remote-core   # or remote-creation-core, remote-player-view, ...
curl -sfLO https://dl.google.com/android/maven2/androidx/compose/remote/$A/$V/$A-$V-sources.jar
unzip -q $A-$V-sources.jar -d $A
```
Put downloads in a scratch directory, not in the repo. Most behavior lives in `remote-core`
(`CoreDocument`, `operations/layout/managers/*`, `operations/layout/modifiers/*`, `Limits`).

---

## Watching releases

RC is alpha and semantics change between releases (alpha20 → alpha23 changed density scaling).
Watch the [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/compose-remote):
`./scripts/check-rc-release.sh` compares our version with Google Maven; CI runs it every Monday
(`.github/workflows/rc-release-watch.yml`, a failed run = new release). Upgrade with
[the playbook](playbooks/upgrade-remote-compose.md), and record changed behaviors here with the version.

## API stability

All creation and player classes are `@RestrictTo(LIBRARY_GROUP)` (alpha20–alpha23) →
[ADR 0002](decisions/0002-confine-restricted-remote-compose-api.md).

---

## Document versions

- `CoreDocument.DOCUMENT_API_LEVEL = 8` in alpha20 and alpha23; header version 1.1.0.
- The writer accepts API levels 6–8 for our documents (level 5 → `Unsupported API level`);
  operations are validated per level/profile at **creation** time (`Operations`,
  `RemoteComposeBuffer.setVersion`), so an unsupported op fails on the server.
- We write profile `RcProfiles.PROFILE_ANDROIDX` (512); the profile is stored in the header
  (`DOC_PROFILES`) and the reader needs it to know e.g. `CORE_TEXT`.

---

## Behaviors that bit us

1. **URL images** — disabled by default, 1x1 size check, synchronous load →
   [ADR 0003](decisions/0003-embed-images-server-side.md). Verified alpha20, alpha23.
2. **Density/units** — table per version in [ADR 0004](decisions/0004-units-dp-and-sp.md).
3. **`height(RcFloat)`** writes `Height(EXACT, 0.0)` unless the expression is written first
   (`written(...)` in the renderer). Verified alpha20, alpha23.
4. `HTag` header tags with `DOC_DENSITY_BEHAVIOR` are passed to `createRcBuffer(profile, *tags)`.
5. Host actions: `Modifier.onClick { hostAction("name") }` → player `addActionCallback { name, value }`.
   The name is a `TextData` UTF-8 string, max `Limits.MAX_STRING_SIZE = 4000` chars;
   `HostNamedActionOperation` also has an optional `valueId` (the callback's `value`). Verified alpha23.
6. `RcFloat` expressions are lazy; `toFloat()` writes them. Text font size accepts an expression
   (NaN-encoded id) and listens to its variables.
7. **`fontSize()`** — `FontTextSize.fontSize()` returns 14.0; use `fontSize() * value / 14` as a
   scale factor to approximate `sp` units. Verify this constant on every RC upgrade (silent failure
   risk: wrong text sizes without any error).
8. **`StateLayout` missing `(mX, mY)` translation & first-frame index 0** — `StateLayout.paint()`
   overrides `Component.paint()` without translating by `(mX, mY)` and without executing direct
   `ComponentData` children, and reads its state integer only in `paint()` (so frame 1 always
   measures/paints child `0`). Declare `remoteInteger(0)` before `StateLayout`, wrap `StateLayout`
   in a `Box`, and order children so child `0` is the initial state. Verified alpha23 (T-008).

---

## Player (Android)

- `RemoteComposePlayer.setDocument(RemoteDocument)`; decode `RemoteDocument(bytes)` off the main
  thread.
- Emulator performance: start the emulator with `-gpu host` (without it ~20x slower).
- Texts from documents appear in the accessibility tree (useful with `uiautomator dump`).

---

## Community projects & experiments

### armcha/remotecompose — Web Editor + JVM Converter + Android Player

<https://github.com/armcha/remotecompose> | Live editor: <https://armcha.github.io/remotecompose/>

Full server-driven UI pipeline outside the Google App Widget path:

```
CREATION (JVM Server)                   PLAYBACK (Android App)
┌────────────────────────┐              ┌────────────────────────┐
│  Web drag-and-drop     │              │  RemoteComposePlayer   │
│  editor (HTML/JS)      │   binary     │  (View-based)          │
│  → JSON config         │──  .rc  ────▶│                        │
│  → RemoteComposeWriter │   files      │  No creation deps!     │
└────────────────────────┘              └────────────────────────┘
```

Key takeaways: creation and playback are fully separable; `RemoteComposeWriter` from
`remote-creation-core` + `remote-creation-jvm` works on pure JVM; `RemoteComposePlayer` is
View-based → wrap in `AndroidView`.

### camaelon/remotecompose-experiments — Multi-platform Players

<https://github.com/camaelon/remotecompose-experiments>

Players for multiple targets: C++/Skia (macOS/iOS/headless), Kotlin Multiplatform (Desktop),
TypeScript (Web/VS Code). The `.rc` binary format is **not Android-only**.

---

## Kotlin Multiplatform (KMP) support

Google/Jetpack has no official KMP support for Remote Compose (Android-only as of 2026-11-07).

| Option | Notes |
|---|---|
| `remotecomposeplayer-cmp` (deanalvero) | Experimental KMP/CMP player for `.rc`; Android, iOS, Web, Desktop |
| camaelon C++/Skia, TypeScript players | Experimental multi-platform readers |

For compose-cast: the JVM server generates `.rc` today; `remotecomposeplayer-cmp` is an optional
future client for iOS/Web → [ADR 0007](decisions/0007-ios-strategy-spike.md).

---

## Official sources

| Resource | URL |
|---|---|
| Conceptual guide | <https://developer.android.com/develop/ui/compose/system/remote-compose> |
| AndroidX releases | <https://developer.android.com/jetpack/androidx/releases/compose-remote> |
| Foundation artifacts | <https://developer.android.com/jetpack/androidx/releases/compose-remote-foundation> |
| Wear OS | <https://developer.android.com/jetpack/androidx/releases/wear-compose-remote> |
| Procedural components guide | <https://github.com/androidx/androidx/blob/androidx-main/compose/remote/remote-creation/doc/guides/PROCEDURAL_COMPONENTS_GUIDE.md> |
| AndroidX source | <https://github.com/androidx/androidx/tree/androidx-main/compose/remote> |
