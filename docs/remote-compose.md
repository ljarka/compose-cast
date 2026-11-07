# Remote Compose — verified knowledge

Facts about `androidx.compose.remote` that compose-cast depends on, **with the version they were
verified on**. Update this file whenever you learn or re-verify something; on an RC upgrade go
through every section.

Current version: **1.0.0-alpha23** (catalog: `remote-compose` in `gradle/libs.versions.toml`).

## Artifacts we use

| Artifact | Used in | Notes |
|---|---|---|
| `remote-creation-core`, `remote-creation-jvm` | `sdk-server` | JVM document creation (`createRcBuffer`, `RcScope`) |
| `remote-core` | `sdk-server`, `sdk-android` | format, `CoreDocument`, reader (`describe()`) |
| `remote-player-core`, `remote-player-view` | `sdk-android` | `RemoteComposePlayer` (View); minSdk 26 |

Also published (unused): `remote-player-compose`, `remote-creation-compose`,
`remote-creation-android`, `remote-creation-expression`, `remote-testing`, `remote-tooling-preview`.
**No iOS / Kotlin Multiplatform artifacts** (checked 2026-11-07) → [ADR 0007](decisions/0007-ios-strategy-spike.md).

## How to research the API (do this instead of guessing)

```bash
V=1.0.0-alpha23; A=remote-core   # or remote-creation-core, remote-player-view, ...
curl -sfLO https://dl.google.com/android/maven2/androidx/compose/remote/$A/$V/$A-$V-sources.jar
unzip -q $A-$V-sources.jar -d $A
```
Put downloads in a scratch directory, not in the repo. Most behavior lives in `remote-core`
(`CoreDocument`, `operations/layout/managers/*`, `operations/layout/modifiers/*`, `Limits`).

## API stability

All creation and player classes are `@RestrictTo(LIBRARY_GROUP)` (alpha20–alpha23) →
[ADR 0002](decisions/0002-confine-restricted-remote-compose-api.md).

## Document versions

- `CoreDocument.DOCUMENT_API_LEVEL = 8` in alpha20 and alpha23; header version 1.1.0.
- The writer accepts API levels 6–8 for our documents (level 5 → `Unsupported API level`);
  operations are validated per level/profile at **creation** time (`Operations`,
  `RemoteComposeBuffer.setVersion`), so an unsupported op fails on the server.
- We write profile `RcProfiles.PROFILE_ANDROIDX` (512); the profile is stored in the header
  (`DOC_PROFILES`) and the reader needs it to know e.g. `CORE_TEXT`.

## Behaviors that bit us

1. **URL images** — disabled by default, 1x1 size check, synchronous load →
   [ADR 0003](decisions/0003-embed-images-server-side.md). Verified alpha20, alpha23.
2. **Density/units** — table per version in [ADR 0004](decisions/0004-units-dp-and-sp.md).
3. **`height(RcFloat)`** writes `Height(EXACT, 0.0)` unless the expression is written first
   (`written(...)` in the renderer). Verified alpha20, alpha23.
4. `HTag` header tags with `DOC_DENSITY_BEHAVIOR` are passed to `createRcBuffer(profile, *tags)`.
5. Host actions: `Modifier.onClick { hostAction("name") }` → player `addActionCallback { name, value }`.
6. `RcFloat` expressions are lazy; `toFloat()` writes them. Text font size accepts an expression
   (NaN-encoded id) and listens to its variables.

## Player (Android)

- `RemoteComposePlayer.setDocument(RemoteDocument)`; decode `RemoteDocument(bytes)` off the main
  thread.
- Emulator performance: start the emulator with `-gpu host` (without it ~20x slower).
- Texts from documents appear in the accessibility tree (useful with `uiautomator dump`).
