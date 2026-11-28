# Feature checklist

Inventory of what compose-cast can do (`[x]`) and the backlog of ideas (`[ ]`), grouped by the
historical stages (Etap 0–12). **This is not the plan:** order and priorities live in
[specs/roadmap.md](../specs/roadmap.md), the work itself in the [task queue](tasks/README.md).
Tick a box when its task is done (Definition of Done in `AGENTS.md`); open items in a finished
(✅) or active (⏳) Etap name their task (checked by `scripts/check-docs.sh`).

### Etap 0 — Technical Spike ✅
- [x] Verify `remote-creation-jvm` works on plain JVM
- [x] Generate and serve Remote Compose binary documents
- [x] Play `.rc` documents in Android with `RemoteComposePlayer`

### Etap 1 — Repository, CI, Tests & Docs ✅
- [x] Gradle multi-module structure; current toolchain (AGP 9.5, Gradle 9.6, Kotlin 2.4, Compose BOM)
- [x] GitHub Actions CI: build, lint, screenshot verification, docs check
- [x] Screenshot tests server → player (`compat-tests`), images in this README
- [x] Agent documentation: index `AGENTS.md`, ADRs, Remote Compose knowledge base, history
- [x] ktlint (Spotless, rules in `.editorconfig`, checked by `verify.sh`) + CI badge

### Etap 2 — Foundations ✅
- [x] Remote Compose alpha20 → alpha23 (units revised, [ADR 0004](decisions/0004-units-dp-and-sp.md))
- [x] Typed actions with parameters (`navigate` with params, `open_url`, `reload`, `track`) — T-001
- [x] **Serializable screen model** (JSON) — one model for the DSL, JSON files, scripts and the admin panel — T-002 ([ADR 0015](decisions/0015-screen-model-is-the-product.md))
- [x] **Per-request rendering** with parameters (`dynamicScreen`, `/screens/movie?id=42`); static
  screens pre-rendered and validated at start — T-003
- [x] `ETag` / `If-None-Match` (`304`), in-memory revalidation cache in the Android SDK — T-003

### Etap 3 — iOS spike: dropped
Android only ([ADR 0022](decisions/0022-android-only.md), owner 2026-11-16).

### Etap 4 — Data sources (APIs) — [ADR 0008](decisions/0008-server-side-data-sources.md)
- [x] HTTP data sources: URL templates with route/query params, secrets from env — T-006
- [x] Bind JSON fields to components (`{{weather.current.temperature}}`); repeat over arrays — T-006
- [x] Loading / error / empty states; per-source timeouts and caching — T-006
- [x] Lookups by more than one value (`append:` with path arguments, day/night icons) — T-041

### Etap 5 — Components for real apps
- [x] Text, Image, Button, Spacer, Expandable (local state PoC — T-008)
- [x] Row, Column, Box; size, weight, alignment, padding, background, border, rounded clip, click on anything — T-012
- [x] Text color/weight/align/lines/ellipsis/marquee; Icon, Divider, Card, button variants — T-013
- [x] Image sizing (aspect, crop, rounded) — T-013
- [ ] Large/many images loaded by the SDK as named bitmaps — T-027
- [x] Horizontal gallery, Flow (chips), grids, scroll on any container — T-014; lists from data — T-006
- [x] Adaptive layouts: FitBox, collapsible rows/columns, min/max sizes — T-015
- [x] Accessibility: content descriptions, roles — T-016
- [ ] Canvas: progress bar/ring, line/bar charts, rating, gradients — T-018
- [x] Components list with each component's image (cut from the phone preview); designer shows the JSON and the compiled RC JSON under the artboard — T-057
- [x] Screens list with a small phone image per screen (the preview renderer, scaled by the server) — T-048 follow-up
- [x] Component designer: draw custom components on an artboard (shapes, gradients, SVG icon import, texts, tap areas, anchors), compiled to RC JSON — T-057 ([ADR 0034](decisions/0034-component-designer.md))
- [x] Custom components written in raw RC JSON (any shape, gradient, vector icon), reused by name, edited in the admin — T-049 ([ADR 0033](decisions/0033-custom-components-raw-rc-json.md))
- [x] Dashboard components on canvas: `gauge`, `sparkline`, `kpi` — T-047 step 1
- [x] Live screens, server: opt-in `live`, `/screens/{id}/live` WebSocket with RC data-update documents (ADR 0029) — T-047 step 2
- [x] Live screens in the app: `sdk-android` keeps the socket open while the screen is shown, data updates in place — T-047 step 3
- [ ] Long-press, double-tap, ripple, haptics; alpha/scale/rotation/offset/z-order — T-019
- [ ] Tabs / bottom navigation defined by the server (app structure, not only screens)
- [x] Brand surface/text/outline colors for component defaults (`brand.json`) — T-040

### Etap 6 — Milestone 1: Example weather app (external project)

> **External example, not part of this repository** ([ADR 0017](decisions/0017-apps-in-own-repositories.md)):
> the weather app is an example of using compose-cast in its own separate repository — screens JSON
> + data sources + `docker-compose.yml` for a VPS (published image, HTTPS) + the Android app
> configured from `app-template`. Anything missing is added to the framework generically, never as
> weather-specific code in this project.

- [x] Image fetches never reach private/loopback addresses (SSRF) — T-026
- [x] Example weather app on [Open-Meteo](https://open-meteo.com) (no API key): current conditions,
  hourly/daily forecast, city list — screens as JSON, data via data sources — built as a separate
  example repository using the published compose-cast artifacts (`v0.1.0`, 2026-11-23)
- [ ] Server install like [Immich](https://immich.app/docs/install/docker-compose): `docker-compose.yml`
  + `example.env` from GitHub Releases, image on GHCR (amd64 + arm64), optional HTTPS (Caddy) — T-023
  ([ADR 0018](decisions/0018-distribution-like-immich.md)); files and workflow done, ticked
  after the first GitHub Release is installed on a VPS (release `v0.1.0` published 2026-11-23, image public)
- [x] Android app without Kotlin: `sdk-android`, `app-shell` and the `io.composecast.app` Gradle
  plugin on a self-hosted Maven repository (Reposilite, [ADR 0019](decisions/0019-self-hosted-maven-reposilite.md)) — T-024;
  `0.1.0` on `maven.jarka.club` (2026-11-23)
- [x] Server serves JSON screens from a directory (`COMPOSECAST_DATA_DIR`, `PORT`), request logging
- [x] Backend in one command: Docker image (amd64 + arm64) + `docker-compose.yml`, screens on a
  volume, secrets in `.env`, healthcheck — T-009
- [ ] Weather app repository, screenshots in this README; RAM/latency measured with per-request rendering — T-025;
  repository, screenshots and measurements done (runs on `v0.1.0`, 2026-11-23), ticked when it runs on a VPS over https

### Etap 7 — App shells & SDKs
- [x] Android SDK: `RemoteComposePlayer` in `AndroidView`, Ktor fetch, loading/error, action router
- [x] Android SDK for external apps, core: explicit API + KDoc, custom request headers (auth),
  `ComposeCastError`, `Closeable` + `rememberComposeCastClient`, R8 consumer rules — T-058
- [x] Android SDK high-level composables without Material: `ComposeCastView` (one screen, loading/error
  slots, live), `ComposeCastFlow` + `ComposeCastNavigator` (saved back stack, Back, action routing) — T-059
- [x] `app-shell` dogfoods the SDK: the zero-code apps run on `ComposeCastFlow` (no private copies) — T-060
- [x] `app-template` configured by one `compose-cast.properties` (name, `applicationId`, server URL,
  frame colors, icon; release = https only) — T-011
- [ ] Navigation/tabs from the server, deep links
- [ ] Disk cache, offline last-known screen — T-028; prefetch; analytics hooks (actions, visibility)
- [ ] Locale: `Accept-Language` → `RenderContext`, localized screen texts
- [ ] Brand tokens → document colors (surface/text/outline done — T-040); light/dark via RC themed colors; native frame theming
- [x] **Version negotiation** (T-004, [ADR 0021](decisions/0021-version-negotiation.md)): client reports the RC document API level;
  server renders for it or answers `426` "update required" (minimum level 8)

### Etap 8 — Admin panel MVP

> **Deployment model:** a user buys a VPS, deploys compose-cast, configures their app in the admin
> panel, and publishes a branded Android app to Google Play. One server = one product.

- [ ] Ktor-served web UI; SQLite storage (Exposed) — one row per screen (canonical screen JSON),
  draft/live status, full history; `<id>.json` import/export ([ADR 0016](decisions/0016-admin-storage-sqlite.md)) — done except export (T-007 A1–A4)
- [x] User accounts (email + bcrypt password), roles: `admin` / `editor` / `viewer`; session cookie;
  first admin from `COMPOSECAST_ADMIN_EMAIL`/`_PASSWORD` env vars (no shared `ADMIN_PASSWORD`)
- [x] MVP: JSON editor with live validation, preview, publish, history — T-007, T-045
- [x] Visual editor for non-developers: component palette, schema-driven property forms — T-029 (data source form: T-031)
- [x] Data source wizard: paste an API URL → list + detail screens, field picker — T-031
- [x] App templates (News, Shop, Events, Weather, Blank): a working app in one click, as drafts — T-030 (ADR 0031)
- [x] Live preview on a phone, refreshed on every saved edit (same WebSocket as live screens, ADR 0029/0030) — T-032
- [ ] AI assistant (optional, own API key): describe a screen → draft; brand palette — T-033
- [x] Preview on a device: temporary `__preview-…` screen + QR code ([ADR 0013](decisions/0013-admin-preview.md), [0027](decisions/0027-draft-preview-ids.md)) in the generic Preview app (T-045);
  in-browser RC player later, if one matures
- [x] Phone frame in the editor without a device: the real player under Robolectric draws each saved draft (optional `preview-renderer` service, [ADR 0032](decisions/0032-phone-preview-rendered-by-robolectric.md)) — T-048
- [ ] Publish (screen goes live); change history per screen (who published, when); one-click rollback
- [ ] **App Settings page:** app name, `applicationId`, server URL, `BrandConfig` (primary colour,
  background colour, logo upload)

### Etap 9 — White-label app export + TV shows milestone

> **App export:** user fills in App Settings, clicks “Export Android project”. The server
> generates a configured ZIP of `app-template` (server URL, app name, applicationId, frame colors,
> launcher icon from the uploaded logo — screen colors come from the server's `BrandConfig`). User unzips, builds in Android Studio, submits to
> Google Play. **No APK is generated server-side** — only a ready-to-build Gradle project.

- [ ] Export writes `compose-cast.properties` + icon from App Settings — T-010 (SDK comes from Reposilite, ADR 0019)
- [x] HTTPS for the VPS: reverse proxy with automatic certificates in `docker-compose.yml` (store builds are https-only) — Caddy profile, T-023
- [ ] Admin panel “Export project” endpoint: generates a ZIP of the configured template
- [ ] Prerequisites: SDK-loaded images (T-027), text input for search (T-021)
- [ ] TV shows app on [TVMaze](https://www.tvmaze.com/api) (no API key): search results, show
  detail, episodes — built **only** in the admin panel; exported as a branded app
- [ ] Screenshots of the TV shows app in this README

### Etap 10 — Interactivity (DivKit core)
- [x] Local state PoC in procedural RC API (`StateLayout` + `setValue` on tap: `Component.Expandable`) — T-008
- [ ] Host → document variables (RC named variables); general local state (`StateLayout`): toggles, tabs — T-017
- [ ] Device-side values: live clock, formatted numbers, light/dark colors, conditions — T-020
- [ ] Forms: fields, validation, submit to API/webhook/database — T-034
- [ ] App user accounts via Supabase (login, per-user data with RLS) — T-035
- [ ] Text input / search (native views via RC `Custom`) — T-021; expressions (conditional visibility, computed text) — T-017, T-020
- [ ] Templates: reusable components; RC macros to shrink documents — T-022
- [ ] Drag/swipe, sensors, particles, sound — T-022

### Etap 11 — Release 0.1.0-alpha
- [ ] Docs: quick start, components reference, data sources, action protocol, compatibility policy
- [ ] Publish the Android SDK and plugin (Reposilite, ADR 0019) + server image (GHCR); tag, CHANGELOG, GitHub Release
  (server image and Android SDK are pulled forward into Milestone 1: T-023, T-024; `v0.1.0` published
  2026-11-23 without a CHANGELOG)

### Etap 12 — Feature flags, A/B tests, analytics, push
- [ ] Feature flags on elements and screens, A/B tests with traffic split — T-038
- [ ] Analytics in the panel (screen views, taps, events; self-hosted) — T-037
- [ ] Push notifications from the panel (FCM) — T-036

Original ideas (detailed by T-038/T-037):
Production SDUI systems (Nubank, Yelp, RevenueCat) treat server-driven screens as an
experimentation platform — no release needed to change what a user sees.

- [ ] Screen targeting: serve a different screen variant based on a user attribute (plan, country,
  app version, platform) passed in the request header or JWT claim
- [ ] A/B experiment definition: two or more screen variants with traffic split %; server records
  which variant was served; client tracks which actions happened
- [ ] Gradual rollout: expose a new screen to X% of requests; ramp up / roll back via the admin panel
- [ ] Feature flags: boolean or string flags embedded in the screen response — the DSL can gate
  components behind a flag, the server resolves them without a client release
- [ ] Experiment results dashboard (conversion per variant, action counts)

### Later / chores
- [ ] `.screen.kts` hot reload (`server-scripting`) + dev-loop push refresh
- [ ] GIF for README; Gradle configuration cache

---
