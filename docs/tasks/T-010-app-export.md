# T-010 — White-label app export (setup wizard)

Status: queued (after T-007) · Roadmap: Etap 9 · Depends on: T-007 (admin panel + App Settings)

## Goal

User clicks "Export Android project" in the admin panel → downloads a configured ZIP of
`app-template` that is ready to build in Android Studio and submit to Google Play.

No APK is generated server-side — only a ready-to-build Gradle project, pre-configured for the
user's server and brand.

## Why

**Deployment model:** one VPS = one product. The user deploys compose-cast, configures their app in
the admin panel, and publishes a branded Android app. Without this export step, they still need to
edit Kotlin/Gradle files manually — this removes that barrier.

## User flow

1. Admin panel → App Settings: fill in app name, `applicationId`, server URL, BrandConfig (colors,
   logo upload).
2. Click "Export Android project".
3. Download a ZIP file: `compose-cast-app-<appname>-<date>.zip`.
4. Unzip → open in Android Studio → build → submit to Google Play.

## Design

### `app-template` configuration

All runtime values must be externalised into `compose-cast.properties` (checked into the template
but overridden at export time):

```properties
# compose-cast.properties (at app-template root)
app.name=ComposeCast Demo
app.applicationId=io.composecast.demo
app.serverUrl=http://localhost:8080
app.brand.primaryColor=#6200EE
app.brand.backgroundColor=#FFFFFF
app.brand.logoPath=  # relative path inside res/drawable; empty = default
```

The Android `build.gradle.kts` reads this file and feeds values into `BuildConfig`, `strings.xml`,
and `applicationId`. The logo is a PNG placed in `res/drawable/` or `res/mipmap-*/` (ic_launcher).

### Export endpoint

```
POST /admin/api/app/export
  → 200 application/zip  (filename: compose-cast-app-<slug>-<yyyymmdd>.zip)
  → 401 if not authenticated
```

Server-side:
1. Read App Settings from DB (name, applicationId, serverUrl, BrandConfig).
2. Load `app-template` from the server's classpath resources or a bundled directory.
3. Substitute values into `compose-cast.properties`.
4. If logo uploaded: include the PNG in `res/drawable/app_logo.png`; replace `ic_launcher` PNGs
   with scaled versions (use Java `ImageIO`; scaling only, no complex generation).
5. Stream the ZIP to the client.

### App Settings page (prerequisite — part of T-007)

The admin panel must have an "App Settings" section (not per-screen, but global):
- App name (displayed in Play Store)
- `applicationId` (reverse-domain format, e.g. `com.example.myapp`)
- Server URL (the VPS URL Android clients connect to)
- Primary colour (colour picker)
- Background colour (colour picker)
- Logo upload (PNG/JPG, max 1 MB; stored in DB as bytes or on disk)

### AI brand setup (optional enhancement)

A text field: "Describe your brand in a few words (e.g. dark blue, minimalist, fintech)".
On submit, calls the configured LLM API (Claude via OpenClaw or similar) to suggest a BrandConfig.
Result shown as a colour palette preview; user can accept or adjust.

This is optional for the first release of T-009. Add after the basic export works.

## Steps (files to touch)

1. `app-template`: externalise all hardcoded values into `compose-cast.properties`; update
   `build.gradle.kts` to read them; update `ComposeCastScreen.kt` / `MainActivity.kt` to use
   `BuildConfig.SERVER_URL`; `ScreenRegistry` reads `BrandConfig` from `BuildConfig` values.
2. `server-app` (or `admin-server` from T-007): `AppSettings` table (Exposed, single-row),
   `AppSettingsRepository`, REST endpoint `GET/PUT /admin/api/app/settings`.
3. `admin-server` (T-007): "App Settings" page in the admin UI (Freemarker form).
4. `admin-server`: `AppExportService` — builds the ZIP; `POST /admin/api/app/export` endpoint.
5. Test: `AppExportServiceTest` — verify the ZIP contains `compose-cast.properties` with correct
   values; verify logo is included when set.

## Acceptance criteria

- `POST /admin/api/app/export` returns a valid ZIP.
- Unzipping and running `./gradlew :app-template:assembleDebug` on the ZIP succeeds without
  editing any file.
- The built APK connects to the SERVER_URL from App Settings (verify with Android Studio Network
  Inspector).
- BrandConfig colors are applied (primary button color matches App Settings).
- `./scripts/verify.sh` green.

## Verification

```bash
# Export the ZIP
curl -u admin:$ADMIN_PASSWORD -X POST http://localhost:8080/admin/api/app/export -o app.zip

# Unzip and build
mkdir -p /tmp/app-export && unzip app.zip -d /tmp/app-export
cd /tmp/app-export && ./gradlew assembleDebug   # must succeed
```

## Docs to update

- `README.md` — tick Etap 9 checkboxes
- `docs/STATUS.md` — update task statuses
- `docs/tasks/README.md` — mark done
- `docs/history.md` — entry for this task
- `app-template/AGENTS.md` — note that values come from compose-cast.properties

## Out of scope

- APK signing / upload to Play Store (user does this manually in Android Studio / Play Console)
- iOS app export (Etap 3 / later)
- AI brand setup (optional; add after basic export works)
- Multi-flavour builds (one export = one app)
