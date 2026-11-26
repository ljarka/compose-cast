# Admin Panel UI Reference Map

When **Debug mode** is enabled in `/admin/settings` (`AppKeys.DEBUG_MODE` = `admin.debugMode`),
every admin heading and collapsible form group displays a copy button (`<@layout.uid "…"/>` in
`server-admin/src/main/resources/templates/admin/_layout.ftl`). Clicking it copies an `admin:…`
identifier to the clipboard so the owner can point an AI agent straight to that UI section.

Every ID also appears verbatim in its `.ftl` template (`<@layout.uid "…"/>`), so searching the
repo for an ID jumps directly to the exact markup. Keep this table current when adding or renaming
headings in `server-admin`.

## Screens list (`/admin/screens`)

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:screens.header` | `screens.ftl` | `ScreenPages.kt` | Screens list page title, quick-action cards (Templates, Wizard), screen cards grid |
| `admin:screens.new` | `screens.ftl` | `ScreenPages.kt` | "New screen" creation form (`POST /admin/screens`) |

## Visual screen editor (`/admin/screens/{id}` and `/admin/screens/{id}/visual`)

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:visual.header` | `visual.ftl` | `VisualPages.kt`, `ScreenPages.kt` | Screen title, status/live badge, Publish & Preview on phone toolbar |
| `admin:visual.preview` | `visual.ftl` | `ScreenPages.kt` | "Preview on your phone" QR code card (`POST …/preview`) |
| `admin:visual.panel.screen` | `visual.ftl` | `VisualPages.kt` | Screen-level properties form (`?node=`) |
| `admin:visual.panel.component` | `visual.ftl` | `VisualPages.kt` | Selected component properties form + Duplicate/Delete menu |
| `admin:visual.panel.source` | `visual.ftl` | `VisualPages.kt` | Selected data source form (`?node=data.<name>`) |
| `admin:visual.panel.value` | `visual.ftl` | `VisualPages.kt` | Selected screen value form (`?node=values.<name>`) |
| `admin:visual.panel.new-source` | `visual.ftl` | `VisualPages.kt` | "Add data source" form (`?node=data.__new__`) |
| `admin:visual.panel.new-value` | `visual.ftl` | `VisualPages.kt` | "Add screen value" form (`?node=values.__new__`) |
| `admin:visual.panel.sources` | `visual.ftl` | `VisualPages.kt` | Data sources list overview (`?node=data`) |
| `admin:visual.panel.values` | `visual.ftl` | `VisualPages.kt` | Screen values list overview (`?node=values`) |
| `admin:visual.panel.list` | `visual.ftl` | `VisualPages.kt` | Component list node overview (e.g. `components.2.empty`) |
| `admin:visual.group.<path>` | `_form.ftl` | `FormSchema.kt`, `VisualPages.kt` | Generated `<details class="group">` section for a nested object or variant (e.g. `admin:visual.group.live`, `admin:visual.group.modifier`, `admin:visual.group.action`) |
| `admin:visual.values` | `visual.ftl` | `VisualPages.kt` | "Screen values" summary card on the Screen node |
| `admin:visual.sources` | `visual.ftl` | `VisualPages.kt` | "Data sources" summary card on the Screen node |
| `admin:visual.code` | `visual.ftl` | `ScreenPages.kt` | Code card: JSON textarea, Remote Compose tab, Save draft / Publish / Preview buttons |
| `admin:visual.history` | `visual.ftl` | `ScreenPages.kt` | Published revisions table with Roll back & Remove actions |

## Fallback raw JSON screen editor & revisions

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:screen.header` | `screen.ftl` | `ScreenPages.kt` | Raw JSON editor header (shown when stored JSON is invalid for visual editor) |
| `admin:screen.preview` | `screen.ftl` | `ScreenPages.kt` | QR code preview card in raw JSON editor |
| `admin:screen.history` | `screen.ftl` | `ScreenPages.kt` | Published revisions table in raw JSON editor |
| `admin:revision.header` | `revision.ftl` | `ScreenPages.kt` | Read-only revision viewer (`/admin/screens/{id}/revisions/{rev}`) |

## Custom components (`/admin/components`)

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:components.header` | `components.ftl` | `ComponentPages.kt` | Components list table with thumbnails and delete menu |
| `admin:components.new` | `components.ftl` | `ComponentPages.kt` | "New component" creation form (`POST /admin/components`) |
| `admin:component.header` | `component.ftl` | `ComponentPages.kt` | Component JSON editor header (`/admin/components/{id}`) |
| `admin:component.preview` | `component.ftl` | `ComponentPages.kt` | Component phone preview card |
| `admin:component.usage` | `component.ftl` | `ComponentPages.kt` | "Use on a screen" JSON snippet card |
| `admin:component.history` | `component.ftl` | `ComponentPages.kt` | Component revision history table |

## Component designer (`/admin/components/{id}/design`)

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:designer.header` | `designer.ftl` | `ComponentPages.kt`, `designer.js` | Designer page header (`{id} Designer`) |
| `admin:designer.layers` | `designer.ftl` | `designer.js` | Layers list and reorder/duplicate/delete buttons |
| `admin:designer.component` | `designer.ftl` | `designer.js` | Component-level settings & parameters panel |
| `admin:designer.code` | `designer.ftl` | `designer.js` | Designer Code panel (JSON & compiled Remote Compose tabs) |
| `admin:designer.properties` | `designer.ftl` | `designer.js` | Selected layer properties panel |
| `admin:designer.phone` | `designer.ftl` | `ComponentPages.kt`, `designer.js` | "On the phone" live player render card |

## Templates (`/admin/templates`)

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:templates.header` | `templates.ftl` | `TemplatesPages.kt` | "Start from a template" page title |
| `admin:templates.applied` | `templates.ftl` | `TemplatesPages.kt` | Post-apply summary card with "Publish all" button |
| `admin:templates.card.<id>` | `templates.ftl` | `TemplatesPages.kt` | Individual bundled template card (e.g. `admin:templates.card.news`) |

## API wizard (`/admin/wizard`)

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:wizard.header` | `wizard.ftl` | `WizardPages.kt` | "Create screens from an API" page header & URL probe form |
| `admin:wizard.fields` | `wizard.ftl` | `WizardPages.kt` | "Fields of one item" probed schema table |
| `admin:wizard.mapping` | `wizard.ftl` | `WizardPages.kt` | "What goes where" role mapping dropdowns (title, subtitle, image, id) |
| `admin:wizard.screens` | `wizard.ftl` | `WizardPages.kt` | "Screens" output ids, data source name & detail mode |

## Users (`/admin/users`, admin only)

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:users.header` | `users.ftl` | `UsersPages.kt` | Users page title and role overview |
| `admin:users.list` | `users.ftl` | `UsersPages.kt` | Accounts table: email, role change, last login, reset password, remove |
| `admin:users.new` | `users.ftl` | `UsersPages.kt` | "Add user" form (email, role, temporary password with generator) |

## Settings (`/admin/settings`)

| ID | Template | Handler | Description |
|---|---|---|---|
| `admin:settings.header` | `settings.ftl` | `SettingsPages.kt` | Settings page title |
| `admin:settings.app` | `settings.ftl` | `SettingsPages.kt` | App name, applicationId, server URL fields |
| `admin:settings.brand` | `settings.ftl` | `SettingsPages.kt` | Brand color inputs (primary, secondary, background, surface, text, outline) |
| `admin:settings.logo` | `settings.ftl` | `SettingsPages.kt` | App launcher logo upload & removal |
| `admin:settings.developer` | `settings.ftl` | `SettingsPages.kt` | Debug mode toggle (enables/hides AI reference ID copy buttons) |
