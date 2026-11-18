# T-040 — Brand theme colors for component defaults (surface, text)

Status: queued (after T-043) · Roadmap: Etap 5 · Depends on: — · Module: protocol, sdk-server

## Goal

Component defaults follow the app's brand instead of fixed Material-purple colors. Found by the
weather app (T-025): with a blue brand, every `card` (and the hourly list) is lavender
(`SURFACE_COLOR #F3EDF7`) unless each screen sets `modifier.background` by hand.

## Why

ADR 0017: app-specific looks are configuration, not per-screen workarounds. Today
`sdk-server/.../render/Style.kt` hardcodes `TEXT_COLOR`, `SURFACE_COLOR`, `DIVIDER_COLOR`,
`PLACEHOLDER_COLOR`; `BrandConfig` only has `primaryColor`, `secondaryColor`, `backgroundColor`, font.

## Design

- `BrandConfig`: optional `surfaceColor` (cards, expandables), `onSurfaceColor` / `textColor`
  (default text), `outlineColor` (divider, outlined borders). `null` = today's constants, so
  existing documents stay byte-identical.
- `RenderEnv` (render package) carries the resolved colors; `Style.kt` keeps the defaults.
- `BrandConfig` is shared with the app (`/brand`): adding fields is a protocol change — the app
  ignores unknown fields (check `sdk-android` decoding is lenient) and needs no change.

## Steps

1. `protocol/BrandConfig.kt` + validation of the colors (registry already checks brand colors).
2. `render/RemoteComposeRenderer.kt` (`RenderEnv`), `Nodes.kt`, `Containers.kt`, `Interactive.kt`,
   `Images.kt` (placeholder), `Modifiers.kt` untouched.
3. Tests: `RemoteComposeRendererTest` (a brand surface color appears in the card's background op);
   screenshot test of a branded card in `compat-tests`.

## Acceptance criteria

- Demo goldens unchanged (no new brand fields in the demo); new screenshot test green.
- The weather app's `brand.json` with `"surfaceColor": "#FFFFFF"` makes the explicit card
  backgrounds unnecessary.

## Docs to update

`protocol/AGENTS.md`, `docs/screen-json-format.md` (brand.json), `sdk-server/AGENTS.md`, history.

## Out of scope

Dark mode (T-020), per-component style presets.
