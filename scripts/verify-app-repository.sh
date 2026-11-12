#!/usr/bin/env bash
# T-024: proves that an app repository builds with no Kotlin — only the files in
# distribution/android-app/ — from the published artifacts. Publishes everything into
# build/maven-dry-run, copies distribution/android-app/ into an empty directory pointed at that
# repository, and builds it. Part of ./scripts/verify.sh (full).
#
#   ./scripts/verify-app-repository.sh            build the debug APK
#   ./scripts/verify-app-repository.sh --install  also install it on the running emulator/device
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

VERSION=0.1.0-SNAPSHOT
REPO="$ROOT/build/maven-dry-run"
APP=$(mktemp -d)
trap 'rm -rf "$APP"' EXIT

echo "Publishing $VERSION to build/maven-dry-run"
rm -rf "$REPO"
./gradlew --console=plain -q -PcomposecastVersion=$VERSION publish :gradle-plugins:publish

cp -R distribution/android-app/. "$APP/"
sed -i.bak "s|https://maven.example.com/releases|file://$REPO|g" "$APP/settings.gradle.kts"
sed -i.bak "s|version \"0.1.0\"|version \"$VERSION\"|" "$APP/build.gradle.kts"
# Debug build against the local dev server; a distinct id so it installs next to the demo.
sed -i.bak -e "s|^app.serverUrl=.*|app.serverUrl=http://10.0.2.2:8080|" \
  -e "s|^app.applicationId=.*|app.applicationId=io.composecast.apprepository|" \
  -e "s|^app.name=.*|app.name=App Repository|" "$APP/compose-cast.properties"
rm "$APP"/*.bak

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -z "$sdk" && -f local.properties ]] && sdk=$(sed -n 's/^sdk.dir=//p' local.properties)
[[ -n "$sdk" ]] || { echo "Android SDK not found (ANDROID_HOME or local.properties)"; exit 1; }
echo "sdk.dir=$sdk" > "$APP/local.properties"

if find "$APP" -name '*.kt' -o -name '*.java' -o -name 'AndroidManifest.xml' | grep -q .; then
  echo "distribution/android-app must not contain code or a manifest"; exit 1
fi

echo "Building the app repository (files: $(cd "$APP" && ls -A | tr '\n' ' '))"
./gradlew --console=plain -q -p "$APP" assembleDebug
apk="$APP/build/outputs/apk/debug/$(basename "$APP")-debug.apk"
[[ -f "$apk" ]] || apk=$(find "$APP/build/outputs/apk/debug" -name '*.apk' | head -1)
[[ -f "$apk" ]] || { echo "No APK built"; exit 1; }

if [[ "${1:-}" == "--install" ]]; then
  "${sdk}/platform-tools/adb" install -r "$apk"
fi
echo "verify app repository: OK"
