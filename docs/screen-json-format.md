# Screen JSON format

The source format of a screen (`ScreenDefinition`, [ADR 0015](decisions/0015-screen-model-is-the-product.md)):
`screens/<id>.json` in a data directory ([ADR 0011](decisions/0011-admin-storage-format.md)), the JSON export of
DSL screens, and later what the admin panel edits. Defined in `protocol` (`Screen.kt`, `Component.kt`,
`ComponentOptions.kt`, `Layout.kt`, `Binding.kt`), validated by `ScreenValidation.kt`.

## Basics

One screen per file, named `<id>.json`. Discriminator `"type"`; defaults (`title` = id,
`style` = `body`, `spacer.height` = 8, `expandable.expanded` = false, empty `params`) may be omitted and are not encoded.
Strict: unknown keys/types fail with the source and the JSON path.

```json
{ "id": "main", "title": "Home", "components": [
    { "type": "text", "text": "Welcome", "style": "h1" },
    { "type": "image", "url": "https://picsum.photos/400/200", "alt": "Hero" },
    { "type": "spacer", "height": 16 },
    { "type": "expandable", "title": "Details", "content": [{ "type": "text", "text": "More info" }] },
    { "type": "button", "label": "Open", "action": { "type": "navigate", "screen": "movie", "params": { "id": "42" } } }
] }
```
Actions: `navigate` (screen, params), `open_url` (url), `reload`, `track` (event, params),
`custom` (payload). Full examples: `examples/demo/screens/`; cases: `ScreenJsonTest`.

### Containers and modifiers (T-012, [ADR 0020](decisions/0020-component-tree-and-modifiers.md))

```json
{ "type": "row", "spacing": 12, "horizontalArrangement": "spaceBetween", "verticalAlignment": "center",
  "children": [
    { "type": "column", "spacing": 8, "children": [ … ],
      "modifier": { "weight": 1, "padding": 12, "background": "#FFFFFF", "cornerRadius": 16,
                    "border": { "width": 1, "color": "#D0C8DC" }, "action": { "type": "navigate", "screen": "detail" } } },
    { "type": "box", "horizontalAlignment": "center", "verticalAlignment": "center", "children": [ … ],
      "modifier": { "width": 48, "height": 48, "shape": "circle", "background": "#03DAC6" } }
] }
```
- `column` (`horizontalAlignment`, `verticalArrangement`, `spacing`), `row` (`horizontalArrangement`,
  `verticalAlignment`, `spacing`), `box` (`horizontalAlignment`, `verticalAlignment`); alignment
  `start|center|end`, arrangement also `spaceBetween|spaceAround|spaceEvenly` (`start` = top).
- `modifier` on every component except `repeat`: `padding` (number or `{start,top,end,bottom}`),
  `width`/`height` (dp, `"fill"`, `"wrap"`), `weight` (direct children of row/column only),
  `background`, `cornerRadius` **or** `shape: "circle"`, `border {width=1, color}`, `action`.
  Unordered box model: size includes padding; background/border/clip cover the whole size; no margin.
- Defaults: children of a column/the screen fill the width, children of a row wrap; `spacer.height`
  is space along the parent's direction. Max nesting depth 32. Cases: `LayoutTest`.
- Styling & composite components (T-013):
  - `text`: optional `color`, `fontWeight` (`thin`…`black`), `fontSize` (sp), `textAlign` (`start|center|end|left|right|justify`), `maxLines`, `overflow` (`clip|visible|ellipsis|startEllipsis|middleEllipsis`), `marquee` (`false`).
  - `image`: optional `contentScale` (`fit|crop|fillBounds|fillWidth|fillHeight|inside|none`), `alpha` (`0..1`), `aspectRatio` (`width/height`).
  - `icon`: `url`, `alt`, `size` (dp, default 24); `divider`: `thickness` (dp, default 1), `color`.
  - `card`: `children`, `spacing` (default 0); defaults to 16 dp padding, 12 dp corner radius, surface background.
  - `button`: `variant` (`filled|outlined|text`), optional leading `icon` URL.
- Collections & scroll (T-014):
  - `modifier.scroll`: `"vertical"` | `"horizontal"` on containers.
  - `flow`: `children`, `horizontalAlignment`, `verticalAlignment`, `maxItemsInEachRow`, `maxLines`.
  - `grid`: `columns` (1..12), `children`, `spacing` (equal-width cells).
- Adaptive layouts & constraints (T-015):
  - `fitBox`: `children`, `horizontalAlignment`, `verticalAlignment` (picks first child that fits).
  - `row` / `column`: `collapsible: true` + child `modifier.collapsePriority` (lower drops first).
  - `modifier`: `minWidth`, `maxWidth`, `minHeight`, `maxHeight` (dp), `fillFraction` (`(0, 1]`).
- Accessibility semantics (T-016):
  - `modifier.semantics`: `{description, role (button|checkbox|switch|radioButton|tab|image|dropdownList|picker|carousel), stateDescription, hidden, enabled}`.
  - `ScreenDefinition.warnings()`: non-fatal warnings (e.g. clickable image/icon without `alt` or `semantics.description`), surfaced in `/screens/{id}/debug`.

### Live screens (T-047, [ADR 0029](decisions/0029-live-screens-websocket-data-updates.md))

```json
{ "id": "ops", "live": { "everyMs": 1000 }, "data": { "m": { "url": "https://…/metrics", "cacheSeconds": 0 } },
  "components": [{ "type": "gauge", "value": "{{m.cpu}}", "label": "{{m.cpu}}%" }] }
```
- `live` (optional; default `everyMs` 1000, allowed 100..3600000): the server renders the screen
  again every `everyMs` and pushes what changed to apps over `/screens/{id}/live`. Without `live`
  the screen is served exactly as before. Data sources still use their `cacheSeconds` — set it
  at or below `everyMs` (0 = fetch every time) or the values only change when the cache expires.

### Dashboard components (T-047, [ADR 0029](decisions/0029-live-screens-websocket-data-updates.md))

Drawn on a Remote Compose canvas; meant for (live) dashboards. Values are **strings**, so they take
`{{bindings}}` like any text.

```json
{ "type": "gauge", "value": "{{metrics.cpu}}", "min": 0, "max": 100, "label": "{{metrics.cpu | round}}%",
  "color": "#6200EE", "trackColor": "#D0C8DC", "size": 120, "thickness": 12 },
{ "type": "sparkline", "values": "{{metrics.history}}", "min": 0, "max": 200, "color": "#6200EE",
  "height": 64, "thickness": 2 },
{ "type": "kpi", "value": "1,284", "label": "Requests / s", "caption": "+4% vs last hour", "color": "#2B8A3E" }
```
- `gauge`: ring filled from 12 o'clock to `value` within `min`..`max` (defaults 0..100; clamped),
  `label` centered inside. `value` is one number after binding (`"42"`, not `"42%"`); a literal
  that is not a number is an error. `size` (dp, default 120) square unless `modifier` sizes it;
  `thickness` < `size / 2`. Colors default to the brand primary / outline color.
- `sparkline`: line through `values`, full width × `height` dp. `values` is a list of numbers
  (`"3, 5, 2, 8"`) or binds a JSON array of numbers (`"{{m.history}}"`); a literal needs ≥ 2
  numbers. Vertical range `min`..`max`, or the values' own range when not given.
- `kpi`: small `label`, big bold `value`, small `caption` (empty ones are not shown); full width.

### On-device screen values & sliders

Reactive numeric values that live on the phone (no server round-trip): declared in screen-level
`values`, written by `slider`, and read via `{{values.<name>}}` (optional `| round[:0..4]`) in
`gauge.value`, `gauge.label`, `text.text` and `kpi` fields.

```json
{ "id": "interactive",
  "values": { "volume": { "default": "40", "min": 0, "max": 100 } },
  "components": [
    { "type": "gauge", "value": "{{values.volume}}", "label": "{{values.volume | round}}%" },
    { "type": "slider", "controls": "volume", "step": 5 }
  ] }
```
- `values.<name>`: `{ "default": "0", "min": 0, "max": 100, "animate": { "durationMs": 2000, "mode": "once|loop|pingPong", "easing": "linear|easeInOut" } }`
  (`default` may be a number or a server `{{binding}}`, e.g. `"{{device.volume}}"`; `animate` drives the value automatically on the phone via RC `animationTime()`).
- `slider`: `{ "type": "slider", "controls": "<name>", "step": 5, "color": "#...", "trackColor": "#..." }`.
  At most one slider per value (and not on an animated value); optional `step` snaps to evenly spaced notches with haptic ticks.

### Data sources and bindings (T-006)

A screen may declare `params` (`{"lat": {"default": "52.52"}}`; no default = required → 400),
`data` (name → `{url, headers, timeoutMs=5000, cacheSeconds=60, optional=false}`, GET + JSON only),
`lookups` (name → value table, `"*"` = fallback) and `error` (components shown when a non-optional
source fails; without them the server answers 502). String fields (`title`, `text.text`,
`image.url/alt`, `button.label`, action strings incl. `modifier.action`) may contain `{{path | filter:arg}}`:
- roots: `params`, a source name, a repeat `as` name, `$index`; steps `.key`, `[n]`;
- filters: `round[:digits]`, `date:'EEE'`, `lookup:table`, `default:'text'`, `upper`, `lower`,
  `append:'text'`, `plain` (HTML from an API → text: tags removed, `<p>`/`<br>` → line breaks, entities decoded);
- a filter argument that is unquoted and has `.` or `[` is a **path** in the same scope (T-041),
  allowed in `append` and `default`: `{{h.code | append:'-' | append:h.is_day | lookup:icon}}` picks
  `"0-1": "☀️"` / `"0-0": "🌙"` (a lookup by two values); a missing path appends `""`;
- missing value → empty string; integral numbers print without `.0`;
- `{{secret.NAME}}` = env `COMPOSECAST_SECRET_NAME`, **only** in `data.*.url`/`headers`;
  scheme + host of a source URL must be literal; substituted values are URL-encoded.

`{"type": "repeat", "items": "shows" | {"day": "meteo.daily.time", "max": "…"}, "as": "d",
"limit": 7, "template": […], "empty": […]}` — an object of paths zips parallel arrays by index.
Everything is checked at load (unknown root/filter/lookup, misplaced secret) with the JSON path.
Full example: `examples/demo/screens/data.json`; cases: `BindingTest`, `DataBindingTest`.

## brand.json

`<data dir>/brand.json` (optional) = `BrandConfig`, served by `GET /brand` and used by the renderer:

```json
{"name": "Weather", "primaryColor": "#1565C0", "secondaryColor": "#90CAF9",
 "backgroundColor": "#F4F8FC", "surfaceColor": "#FFFFFF", "textColor": "#0D2A4A", "outlineColor": "#BBDEFB"}
```

Colors are `#RRGGBB` or `#AARRGGBB`. `primaryColor`: buttons, expandable `+`/`−`; `backgroundColor`:
the screen (default `#FFFFFF`); optional (T-040) `surfaceColor`: card and expandable background
(default `#F3EDF7`), `textColor`: text without `color` (default `#1C1B1F`), `outlineColor`: divider
without `color` (default `#D0C8DC`). A component's own `color` / `modifier.background` wins.
