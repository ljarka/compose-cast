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

`remote-creation-compose` likely requires a Compose runtime, which does not run on a plain JVM
server without Compose Multiplatform. compose-cast uses `remote-creation-core` + `remote-creation-jvm`
for server-side document creation — pure JVM, no Compose runtime dependency.

If Compose Multiplatform support for `remote-creation-compose` is confirmed in a future RC release,
evaluate migrating to the higher-level DSL (task: spike in Etap 1 or Etap 2 upgrade).

### Component catalog (from official docs, alpha23)

| Component | Type | Notes |
|---|---|---|
| `RemoteBox`, `RemoteRow`, `RemoteColumn` | Basic layout | Standard containers |
| `RemoteFitBox`, `RemoteCollapsibleRow`, `RemoteCollapsibleColumn` | Adaptive layout | Handle dynamic screen sizes |
| `RemoteMaterialTheme` | Theming | Color + typography for Wear OS |
| `RemoteCanvas` | Custom drawing | Records draw commands; runs on player host |
| `RemoteText` | Text | Accepts `RemoteString` for dynamic text |

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
