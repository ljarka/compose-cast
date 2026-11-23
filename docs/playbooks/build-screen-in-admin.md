# Playbook — build a screen in the admin panel (no JSON)

For app owners (T-029, [ADR 0028](../decisions/0028-visual-editor-htmx-server-state.md)). Needs
the server in admin mode ([install-server.md](install-server.md#admin-panel-t-007)) and an
`editor` or `admin` account.

1. **Screens → New screen**: an id (`a-z`, `0-9`, `-`, `_`; it is the address the app asks for,
   e.g. `main`) and a title. Then **Visual editor**.
2. **Screen** (top of the tree): title (may use `{{params.x}}`), and — for data from an API —
   **Data** (`{"name": {"url": "https://…"}}`), **Params**, **Lookups** as JSON for now (a form for
   data sources is T-031).
3. **Add**: pick a type, then *after this* (next to the selected component), *inside* (into a
   column, row, card, …) or *to this list* (select a list heading in the tree, e.g. a repeat's
   `empty`). A `repeat` needs a data source first; it lists the screen's first one — set *Items*
   and *As* in its form (renaming *As* renames its uses in the template).
4. **Arrange**: ↑/↓ within the list, *Into above* (into the container just above), *Out* (out of
   the container), *Wrap in* (put it into a new column/row/card/…), *Duplicate*, *Delete*.
5. **Properties**: the form of the selected component; required fields have a `*`, the less
   common ones are under *Modifier* (padding, size, background, corner radius, border, tap action,
   accessibility). Colors are `#RRGGBB` or `#AARRGGBB`; sizes are dp, `fill` or `wrap`. **Save**.
6. Every change is saved as a **draft** only if the whole screen is valid; otherwise the editor
   says which field (`components[2].color: …`) and keeps what you typed. Apps keep getting the
   live version.
7. **JSON editor, publish, history** page: *Preview on phone* (QR code for the compose-cast
   Preview app), *Publish* (live from the next request), *Roll back* in the history.

Agents: `BuildFromEmptyTest` builds the weather app's `main` and every demo screen this way over
HTTP; extend it when the editor gains an operation. Look at pages with headless Chrome
([server-admin/AGENTS.md](../../server-admin/AGENTS.md#tests)).
