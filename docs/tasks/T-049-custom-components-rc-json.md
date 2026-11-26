# T-049 — Custom components written in RC JSON

Status: queued (not ordered yet — owner places it in the roadmap) · Roadmap: after the success criteria (owner 2026-11-26) · Depends on: T-007, T-048, ADR 0033 · Module: protocol, sdk-server, server-store, server-admin, compat-tests

## Goal
A custom component, for example a gradient card with a vector icon in the brand color, is written
once as **raw Remote Compose JSON**. It is saved under a name with typed params and used on any
screen as `{ "type": "custom", "component": "parcel-tile", "args": { … } }`. The server renders it
into the same RC document as the rest of the screen.

## Why
Owner, 2026-11-26: designs need any shape and custom components. RC JSON can express everything RC
draws, and LLMs write it well. Decision and rejected options: [ADR 0033](../decisions/0033-custom-components-raw-rc-json.md).

## Spike (done 2026-11-26)
Branch `spike/rc-json-fragment` (`compat-tests/.../RcJsonFragmentSpikeTest.kt` + bridge in package
`androidx.compose.remote.creation.json`). Results:
- A fragment spliced mid-`Column` renders, and its `hostAction` reaches the app.
- `definePattern` works.
- There is no `image` component, and `canvas` drops `children`.
Facts: [remote-compose.md → RC JSON](../remote-compose.md#rc-json-remotecomposejsonparser--spike-for-custom-components-verified-alpha23-2026-11-26).
**Not yet checked on the emulator**: do this in K2.

## Design
- **protocol:**
  - `Component.Custom(component, args: Map<String, String>, modifier)`. `args` are strings, so
    they take `{{bindings}}`.
  - `ComponentDefinition(id, version, rcVersion, minApiLevel, params: Map<String, ParamType>,
    body: JsonObject)`. `ParamType`: `text`, `color`, `number`, `action` (`image` comes later, see
    Out of scope).
  - `ComponentJson` codec. Validation: unknown component id, missing or extra args, arg type.
- **sdk-server `render/`:**
  - `RcJsonComponents.kt`: substitution in the JSON tree (`{{params.x}}` only in string values;
    `action` args → `ActionCodec.encode` as a `hostAction` name), then the parse.
  - Bridge file `RcJsonBridge.kt`: lives in `render/` but declares package
    `androidx.compose.remote.creation.json`, and calls `parseComponent`. Check that ktlint accepts
    a package that does not match the directory. If it does not, add a narrow suppression with a
    comment pointing to ADR 0033.
  - Whitelist and limits in `validateBody()`:
    - types: `box`, `row`, `column`, `text`, `canvas`, `spacer`, `flow`, `fitBox`,
      `collapsibleRow`/`collapsibleColumn`;
    - canvas commands: paint (no `runtimeshader`/AGSL), `draw*` except bitmaps;
    - modifiers: all except `include*`;
    - body ≤ 64 KB, ≤ 1000 nodes + commands;
    - a `canvas` with `children` is an error.
  - Errors use our path plus the parser's `ContextPath`.
  - `org.json:json` in `libs.versions.toml` + `sdk-server`.
  - `composeCast { componentsFrom(dir) }` + `component(definition)`.
  - Client API level below `minApiLevel`, or a `broken` definition → nothing is rendered, plus a
    warning in `/debug`.
- **server-store / server-admin:**
  - `components` table with drafts, publish and history, like screens.
  - Admin page "Components": list, JSON editor with live validation, `preview-renderer` image with
    sample args.
  - The visual editor palette gets a "My components" section with a form generated from `params`.
- **Live screens (ADR 0029):** a text arg bound to data must update by data-update document. Add a
  test with `LiveFrames`.

## Steps
1. **K1 model:** `Custom`, `ComponentDefinition`, codec, validation, `ScreenJsonTest` cases.
2. **K2 renderer:**
   - bridge, substitution, whitelist, `RcJsonComponentsTest`;
   - demo `examples/demo/components/parcel-tile.json` + screen `custom` (two tiles with different
     args, one bound to data);
   - golden in `compat-tests` (look at the PNG);
   - **emulator** + Maestro tap → navigate.
3. **K3 admin:** store, Components page, preview, palette section, admin tests.
4. **K4 versioning:**
   - `rcVersion` check on start and in `verify.sh` (render every definition in `examples/` and
     `app-templates`); `broken` status and fallback;
   - step in `docs/playbooks/upgrade-remote-compose.md`.
5. **K5 patterns (optional):** `definePattern` for a component used ≥ N times (first pass before
   `root {}`). Measure the document size gain before keeping it.

## Acceptance criteria
- The `custom` demo screen renders the same on Robolectric (golden) and on the emulator, and taps
  route to the app.
- An invalid body (unknown type, `canvas` + `children`, `runtimeshader`, too big, bad action) is
  rejected with a path. Nothing is rendered silently wrong.
- Changing `rcVersion` to a fake older release marks definitions for re-validation. A broken one
  falls back without failing the screen.
- `./scripts/verify.sh` green.

## Docs to update
`docs/screen-json-format.md` (`custom` + definition format, Compose modifier order note),
`docs/remote-compose.md`, `protocol/AGENTS.md`, `sdk-server/AGENTS.md`,
`server-store/AGENTS.md`, `server-admin/AGENTS.md`, `docs/admin-editor.md`,
`docs/feature-checklist.md`, `docs/history.md`, upgrade playbook.

## Out of scope
- AI chat that writes bodies: T-033 (prompt = this format's docs + whitelist; the loop feeds
  validation errors and the preview PNG back).
- `image` params: need the server to fetch and `defineBitmap` through T-026/T-027.
- Native views (`Custom` in RC), shaders, particles, sound.
