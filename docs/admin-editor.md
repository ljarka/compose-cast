# Admin editor — visual editor, palette, data source wizard, field picker

How the screen-building parts of the admin panel (`server-admin`) work and why. Index of the module:
[server-admin/AGENTS.md](../server-admin/AGENTS.md). Decisions: [ADR 0028](decisions/0028-visual-editor-htmx-server-state.md)
(HTMX, state on the server, forms from descriptors), [ADR 0008](decisions/0008-server-side-data-sources.md)
(data sources and keys stay on the server). Owner's guide: [playbooks/build-screen-in-admin.md](playbooks/build-screen-in-admin.md).

## Decisions

- **Visual editor (T-029 C2, ADR 0028):** the form engine knows only kinds, never components: a
  new component/field is editable without code; a new **custom serializer** needs a case in
  `FormSchema.kind` (today: `Dimension`, `JsonElement`-described ones → JSON value). Values come
  from the component with defaults written out; `parse` leaves unset optional fields out (and an
  optional group nobody filled in, so a required field inside it cannot create it). Component lists
  are never form fields — they are the tree, kept from the stored JSON on save. Saving = the whole
  screen through `ScreenJson.check`, so the visual and JSON editors share one draft and one rule set.
- **Palette (C3):** structural edits are pure functions on the JSON (`Palette`), then the same
  check + save; a refused edit (`IllegalArgumentException`, or an invalid screen such as an
  expandable without content) shows the message and changes nothing. A new component type needs a
  starter in `Palette.starters` and a palette group (`PaletteTest` fails otherwise). The error list
  shows in the tree once the screen has data sources. Renaming a repeat's alias renames
  `{{alias…}}` in its lists (`Bindings.rename`).
- **Wizard (T-031):** the probe's limit is the screens' own 2 MB; lists in generated screens are
  capped at 20 items because images are embedded (ADR 0003; SDK-loaded images: T-027). A key in a
  header or in a URL parameter named like a key (`key`, `apikey`, `token`, …) must be
  `{{secret.NAME}}`, otherwise nothing is created. `ApplicationCall.screenId` reads the **path**
  parameter only (Ktor's `parameters` also holds the query).
- **Live preview (T-032, [ADR 0030](decisions/0030-live-draft-previews.md)):** "Preview on phone"
  keeps one preview per user and screen; every save (JSON editor, every visual edit) updates it and
  the phone follows within about a second. Logout ends it.
- **Live (T-047):** the screen form shows `live` as an optional group ("Every ms", generated from
  `Live`); filling it in makes the screen live, clearing it makes it a normal screen again
  (`FormSchemaTest`). Publishing reaches open live connections on their next tick.
- **Data sources (T-031 W3):** not a JSON field of the screen form but tree nodes `data.<name>`
  whose form is generated from `DataSource` (`FormSchema.sourceFields`); "Add data source" (name +
  address) and "Delete this data source" are `Palette.addSource` / `deleteSource`. Saving the
  screen form keeps `id`, `data`, `components`, `error` from the stored JSON. Deleting a source that
  components still bind to is refused by the normal check.
- **Field picker (T-031 W3):** `GET …/visual/fields?node=` (HTMX fragment `_fields.ftl`):
  `DataFields` probes each source with `ApiProbe` (params → their defaults, secrets from env) and
  lists `{{source.path}}` with samples (objects 3 levels deep, lists as one entry, max 80); inside a
  repeat's `template` the alias' fields of the first item come first (`{{item.name}}`, `{{$index}}`;
  zipped items list their keys). A chip inserts its binding at the cursor of the last focused text
  field of the property form — about 15 lines of inline vanilla JS in `visual.ftl` (the tech stack
  allows small JS islands; no library). Probed on demand (button), not on every page view.

## Tests

`DataFieldsTest` (picker scopes with real samples, source nodes add/edit/delete over HTTP, the
fragment). `AdminTestHost` (temp database + routes on the Ktor test host, `loginAs(role)`). `AdminPanelTest`
(redirect without session, env admin login + cookie attributes, logout, wrong password/unknown
email, viewer 403 / editor allowed on every mutating route found in the routing tree, HTTPS
message, `allowHttp`, HTML escaping, bootstrap rules). `ScreenPagesTest` (list + statuses, create,
invalid edit → problems with paths and nothing saved, canonical draft leaves `live()` unchanged,
validate fragment, editor vs. viewer page, no textarea breakout, publish + who + draft cleared,
invalid/failed publish changes nothing, rollback + revision page, preview QR + nothing saved). `WizardScreensTest` (generated screens render with the 3 fixture APIs' data via `composeCast` + fake loaders),
`WizardPagesTest` (probe page, create → drafts + visual editor, raw keys refused, no list / HTTP 404
explained; the test host's `apiResponses` fake the probe). `ApiShapeTest` (3 fixture APIs in `src/test/resources/fixtures/apis/`), `ApiProbeTest` (fake DNS + HTTP:
private hosts and private redirects never contacted). `PaletteTest` (starters valid, edits keep the selection, nesting, repeat needs data).
`BuildFromEmptyTest` (**acceptance**: the weather app's `main` — `src/test/resources/fixtures/weather-main.json`
from ljarka/weather-app `f0f729b` — and every demo screen built from empty over HTTP with only
New screen, forms and palette operations, byte-identical). `FormSchemaTest` (all component types known, no unexpected JSON-value fields, every component of
`examples/demo` → form values → back unchanged). `VisualPagesTest` (tree, selected form, save one
node, path-named errors keep the input, screen fields, action type switch, viewers disabled, every
node of every demo screen opens and saves unchanged). `SettingsPagesTest` (save brand +
app keys + logo, remove logo, field-named errors keep the input, failed rebuild saves nothing,
admin-only). The host's fake `publishing`
writes straight to the store (`failWith` simulates a build failure).

## Looking at a page

No browser tool needed (2026-11-23): run admin mode, fetch the page with the
session cookie and let headless Chrome draw it (the CSS is inline; HTMX loads from the CDN):

```bash
curl -s -b jar "localhost:8095/admin/screens/main/visual?node=components.0" > page.html
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu \
  --hide-scrollbars --window-size=1280,1300 --screenshot=page.png "file://$PWD/page.html"
```

An HTMX fragment (e.g. the field picker) is not in the saved page: fetch it with curl as well and
put it into the page's placeholder (`<div id="data-fields">`) before the screenshot. Clicks and
scripts cannot be exercised this way.
