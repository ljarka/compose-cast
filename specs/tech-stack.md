# Tech stack

> Part of the project constitution: [mission](mission.md) (why) · **tech stack** (how) ·
> [roadmap](roadmap.md) (what and when). Exact versions live in `gradle/libs.versions.toml`;
> single decisions in [docs/decisions/](../docs/decisions/README.md) (ADRs).

## Shape

```
admin panel (browser) ─┐
JSON files / DSL ──────┼─► ScreenDefinition ─► server renders per request ─► Remote Compose doc ─► Android app
external HTTP APIs ────┘     (one model)         (data bound, images embedded)                    (RC player + thin shell)
```

One Kotlin codebase: server (JVM) and Android client share the `protocol` module.

## Choices

| Area | Choice | Why | Tradeoffs considered |
|---|---|---|---|
| Language | **Kotlin** everywhere (JVM 21 server, Android client, Gradle plugin) | One language and shared model (`protocol`) across server and app | Kotlin on the server narrows the contributor pool vs. TypeScript/Go; worth it for sharing types with Android |
| Wire format | **AndroidX Remote Compose** 1.0.0-alpha (creation API on the server, official player in the app) | Native rendering and on-device interaction without writing our own renderer (ADR 0001) | **Biggest risk:** alpha, `@RestrictTo` API that changes between releases. Contained in one package (`sdk-server/.../render/`, ADR 0002); RC behavior verified from sources, never guessed |
| Screen model | `ScreenDefinition` + JSON (`ScreenJson`), kotlinx.serialization | Serializable, validated (`problems()`), the contract for files, DSL and admin (ADR 0015) | Own format instead of DivKit's JSON: full control, no ecosystem to borrow |
| Server | **Ktor 3** (Netty), logback | Lightweight, Kotlin-native, small image | Spring would bring more batteries (security, data) at the cost of size and startup |
| Rendering | Per request, on the server; images fetched and embedded server-side (ADR 0003), SSRF-guarded | Client stays dumb; data and keys stay on the server | Server CPU per request (measured: p50 3 ms warm, T-025) instead of client-side templating |
| App data | **External HTTP JSON APIs** bound on the server (data sources, ADR 0008) | Any API works; secrets never reach the app | No built-in content database; owners need an API (or the planned forms/auth integrations) |
| Admin storage | **SQLite** via Exposed, one file in the data dir (ADR 0016); *files mode* (`screens/*.json`) stays for read-only installs | Zero-ops, backs up as one file, enough for one product | No horizontal scaling / concurrent writers at scale — acceptable for one server = one product |
| Admin UI | **Freemarker + HTMX**, no npm (ADR 0012); small vanilla JS islands allowed. **Before the visual editor (T-029) an ADR decides** whether a JS framework is needed | No frontend toolchain, one deployable, simple for agents | A drag & drop editor may outgrow HTMX; revisit with evidence rather than up front |
| Auth (admin) | Own accounts: email + bcrypt, roles `admin`/`editor`/`viewer`, session cookie, first admin from env | Self-hosted, no external IdP needed | Less than OAuth/SSO; can be added later behind the same roles |
| Android app | **No code in the app repository:** `io.composecast.app` Gradle plugin + `app-shell` + `sdk-android` (Ktor client, RC player); configured by `compose-cast.properties` (ADR 0017) | Owners never touch Kotlin; framework updates are a version bump | Customization limited to configuration; deeper changes go into the framework generically |
| Preview | **Generic "compose-cast Preview" app** connected by QR code to the owner's server and draft | Works before the owner's own app exists; one-time install | Supersedes ADR 0013's "own debug build" (new ADR needed); a browser RC player would be nicer but is not available |
| Building the owner's app | **Exported app repository with a GitHub Actions workflow** that builds a signed AAB (signing from T-044, keystore in GitHub secrets) | Non-developer needs no local toolchain; the VPS stays small | Depends on a GitHub account; building on the server was rejected (multi-GB image, heavy VPS) |
| Server distribution | Docker image on **GHCR** (amd64 + arm64), `docker-compose.yml` + `example.env` from GitHub Releases, optional **Caddy** for HTTPS (ADR 0018) | One-command install like Immich | Docker required on the VPS |
| Android artifacts | Self-hosted **Reposilite** Maven repository (`maven.jarka.club`, ADR 0019) | Full control, no Maven Central process | Self-hosted infrastructure to keep running |
| Testing | kotlin.test + Ktor test host (JVM); **Robolectric + Roborazzi** screenshot goldens server → player (ADR 0006); **Maestro** flows on the emulator | Rendering bugs are only caught visually / on a device | Goldens need care across macOS/Linux (0.02% tolerance) |
| Quality gates | `scripts/verify.sh` = CI: docs check, architecture rules (`check-architecture.sh`, ADR 0010), Spotless/ktlint, builds, lint, screenshots | Executable rules instead of conventions; agents run the same command as CI | Slower local loop (`--quick` for server-only work) |
| Build | Gradle 9 (Kotlin DSL), AGP 9 (built-in Kotlin), version catalog | Standard Android tooling | AGP majors bring migrations (playbooks in `docs/playbooks/`) |

## Constraints

- Android only; minSdk 26, targetSdk 36, compileSdk 37.
- Self-hosted on one small VPS (heap capped via `JAVA_OPTS`, e.g. `-Xmx256m`).
- No secrets in git (keystores, `.env`, API keys); passwords from env / CI secrets.
- New dependencies go into `gradle/libs.versions.toml`; a change of an accepted choice above needs
  a superseding ADR first.
