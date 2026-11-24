#!/usr/bin/env bash
# Live draft preview end to end (T-032, ADR 0030) on the emulator:
#   1. log in to the admin panel, press "Preview on phone" for SCREEN (POST …/preview),
#   2. open the Preview app with the same link the QR code carries,
#   3. save an edited draft (POST /admin/screens/SCREEN) and expect the phone to show it in < 3 s.
# Needs: the server in admin mode (COMPOSECAST_MODE=admin, COMPOSECAST_ADMIN_ALLOW_HTTP=true for
# plain http), the Preview app installed (./gradlew :app-preview:installDebug), an emulator, Maestro.
#
#   SERVER=http://localhost:8095 EMAIL=… PASSWORD=… ./scripts/verify-live-preview.sh [screen.json]
set -euo pipefail
cd "$(dirname "$0")/.."
SERVER=${SERVER:-http://localhost:8080}
EMAIL=${EMAIL:?admin e-mail}
PASSWORD=${PASSWORD:?admin password}
FILE=${1:-examples/demo/screens/main.json}
PHONE_SERVER=${PHONE_SERVER:-$(sed -E 's#//(localhost|127\.0\.0\.1)#//10.0.2.2#' <<<"$SERVER")}
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

SCREEN=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$FILE")
ORIGINAL=$(cat "$FILE")
MARK="Live preview $(date +%H%M%S)"
EDITED=$(python3 -c 'import json,sys; s=json.load(open(sys.argv[1])); s["components"]=[{"type":"text","text":sys.argv[2],"style":"h2"}]+s.get("components",[]); print(json.dumps(s))' "$FILE" "$MARK")
ORIGIN="Origin: $SERVER"

curl -sf -o /dev/null -c "$WORK/c" -H "$ORIGIN" --data-urlencode "email=$EMAIL" --data-urlencode "password=$PASSWORD" "$SERVER/admin/login"
curl -sf -o "$WORK/p.html" -b "$WORK/c" -H "$ORIGIN" --data-urlencode "json=$ORIGINAL" "$SERVER/admin/screens/$SCREEN/preview"
ID=$(grep -o '__preview-[0-9a-f]\{32\}' "$WORK/p.html" | head -1)
[ -n "$ID" ] || { echo "no preview id in the admin page"; exit 1; }
echo "preview of '$SCREEN' opened"

URL=$(python3 -c 'import sys,urllib.parse as u; print(u.quote(sys.argv[1], safe=""))' "$PHONE_SERVER")
"$ADB" shell am force-stop io.composecast.preview
"$ADB" shell am start -a android.intent.action.VIEW -d "'composecast://connect?url=$URL&screen=$ID'" io.composecast.preview >/dev/null
sleep 6 # app start and first frame (a fresh install compiles for a few seconds)

curl -sf -o /dev/null -b "$WORK/c" -H "$ORIGIN" --data-urlencode "json=$EDITED" "$SERVER/admin/screens/$SCREEN"
echo "draft saved with \"$MARK\""
maestro test -e TEXT="$MARK" app-template/maestro/preview-shows.yaml
echo "OK: the phone showed the saved edit"
