# T-013 — Text and image options, Icon, Divider, Card, button variants

Status: ready · Roadmap: Etap 5 · Depends on: T-012

## Goal

Expose every option RC has for text and images, plus the small composite components real apps
use: `icon`, `divider`, `card`, button variants.

## Why

`Text` today has only a style (h1…caption); `Image` only url/alt with a fixed height. RC alpha23
supports much more (`docs/remote-compose.md` → Procedural catalog, rows Text / Image / Decoration).

## Design

- **Text**: `color`, `fontWeight` (check `RcFontWeight` values in `RcTypes.kt`), `fontSize` (sp,
  overrides style), `textAlign` (start/center/end/justify — whatever `RcTextAlign` has),
  `maxLines`, `overflow` (`clip|visible|ellipsis|startEllipsis|middleEllipsis`), `marquee: true`
  (`Modifier.marquee`, check its parameters).
- **Image**: `contentScale` (`fit|crop|fillBounds|fillWidth|fillHeight|inside|none`), `alpha`,
  `aspectRatio` (compute height from width server-side or via expression — verify), sizing through
  the T-012 modifier, rounded/circle via `modifier.cornerRadius`/`shape`.
  *Found in T-012:* an image without `modifier.height` is sized for the screen's full width
  (wrong inside rows/cards), and `Fit` letterboxes inside a box of another aspect, so rounded
  corners do not show (docs/remote-compose.md #12) — `aspectRatio` and `crop` fix both; update the
  `layouts` demo cards to use them.
- **Icon** (`"type": "icon"`): image URL rendered with `Icon` (Fit), default size 24 dp, optional
  tint only if RC supports it (check; otherwise document "no tint").
- **Divider**: `thickness` (dp, default 1), `color`; = `Box(fillMaxWidth, height, background)`.
- **Card**: container (children) with defaults: padding 16, corner 12, surface background from
  brand, optional border — a composite of T-012 primitives, no new RC op.
- **Button variants**: `variant: filled|outlined|text`, optional leading `icon`.

## Steps

Follow [add-component.md](../playbooks/add-component.md) per component; one demo screen
`components` (+ JSON copy) and one screenshot test covering all variants.

## Acceptance criteria

- Every option above is in the model, DSL, JSON, renderer, with a `describe()` assertion.
- Ellipsis with `maxLines = 1` verified on the emulator (long text truncated, not clipped mid-glyph).
- Screenshot golden reviewed; `./scripts/verify.sh` green.

## Docs to update

`protocol/AGENTS.md`, `sdk-server/AGENTS.md`, README (Defining screens, Etap 5 boxes, screenshot),
`docs/remote-compose.md` (verified behavior: marquee, contentScale, font weights), `docs/history.md`.

## Out of scope

Brand/theme tokens and light/dark (Etap 7, T-020), accessibility semantics (T-016).
