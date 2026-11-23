# Playbook — the compose-cast Preview app

One generic Android app that shows any compose-cast server, so an owner sees their screens on a
phone before their own app exists (T-045, [ADR 0024](../decisions/0024-generic-preview-app.md),
[ADR 0026](../decisions/0026-preview-connect-page.md)). Module: [`app-preview`](../../app-preview/AGENTS.md).

## Using it (owners)

1. On the phone, open `https://<your server>/connect` (the admin panel's QR code points there) and
   tap **Open in compose-cast Preview**. Without the app installed the button downloads
   `compose-cast-preview.apk` from the latest GitHub Release (allow installing from the browser).
2. The app opens your server's `main` screen and remembers the server. **Change server** in the top
   bar goes back to the connect screen; the address can also be typed there
   (`https://app.example.com`, or `http://192.168.1.10:8080` on a local network).

## Release signing (owner, once)

`.github/workflows/release.yml` (job `preview-apk`) attaches a signed `compose-cast-preview.apk` to
every published release. Updates of an installed APK must be signed with the **same** key, so it
lives in GitHub secrets, not in the repository. Until the secrets exist the job only warns.

```bash
keytool -genkeypair -keystore preview.jks -alias preview -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=compose-cast Preview"          # asks for the store and key password
base64 -i preview.jks | gh secret set COMPOSECAST_PREVIEW_KEYSTORE_BASE64
gh secret set COMPOSECAST_PREVIEW_STORE_PASSWORD   # paste the store password
gh secret set COMPOSECAST_PREVIEW_KEY_PASSWORD     # paste the key password
```

Keep `preview.jks` and the passwords in your password manager: losing them means users have to
uninstall the Preview app once. Never commit the keystore (`*.jks` is gitignored).
Version: `versionName` = the release tag without `v`, `versionCode` = the workflow run number.

## Checking it (agents)

```bash
./gradlew :app-preview:installDebug
adb shell pm clear io.composecast.preview                       # forget the server
adb shell am start -a android.intent.action.VIEW -d "composecast://connect?url=http%3A%2F%2F10.0.2.2%3A8080"
maestro test -e SERVER=http://10.0.2.2:8080 app-preview/maestro/connect.yaml   # through Chrome
```

- Port 8080 on the host may be taken (e.g. a Docker server): run the server on another `PORT` and
  pass it in `SERVER`.
- An emulator started without `-gpu host` draws Chrome's web content black (seen 2026-11-23, API
  37): the Maestro flow then cannot find the button. Fire the page's link directly instead —
  `adb shell "am start '<intent://… href from curl localhost:PORT/connect>'"` parses it like Chrome.
- The local build of the release variant is unsigned (no secrets) but must build: `verify.sh` runs
  `:app-preview:assembleRelease` (plain http allowed in the Preview app's release builds).
