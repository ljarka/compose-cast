#!/usr/bin/env bash
# Smoke test of the preview renderer image (T-048); run via `./scripts/verify.sh --renderer`
# (CI job `preview-renderer`). Needs JDK 21, the Android SDK and Docker.
# Builds the renderer (installRenderer) and its linux/amd64 image, starts it, starts server-app on
# examples/demo, and renders the demo `main` screen: the answer must be a 1078×2399 PNG
# (Pixel 7 at 2.625x). Bytes that are not an RC document must get 400.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null || export PATH="$HOME/.docker/bin:$PATH"
command -v docker >/dev/null || { echo "docker not found"; exit 1; }

IMAGE=compose-cast-preview-renderer:verify
NAME="compose-cast-renderer-verify-$$"
RENDERER_PORT="${VERIFY_RENDERER_PORT:-18090}"
SERVER_PORT="${VERIFY_RENDERER_SERVER_PORT:-18081}"
WORK=$(mktemp -d)
SERVER_PID=""

cleanup() {
  status=$?
  if [[ $status -ne 0 ]]; then echo "--- renderer logs"; docker logs "$NAME" 2>&1 | tail -40 || true; fi
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null || true
  rm -rf "$WORK"
}
trap cleanup EXIT

wait_for() { # url seconds
  for _ in $(seq 1 "$2"); do curl -sf "$1" >/dev/null && return 0; sleep 1; done
  echo "not ready after $2 s: $1"; return 1
}

./gradlew --console=plain -q :preview-renderer:installRenderer :server-app:installDist

echo "Building $IMAGE (linux/amd64)"
docker build -q --platform linux/amd64 -f preview-renderer/Dockerfile -t "$IMAGE" preview-renderer/build/renderer >/dev/null
docker run -d --name "$NAME" --platform linux/amd64 -p "127.0.0.1:$RENDERER_PORT:8090" "$IMAGE" >/dev/null

COMPOSECAST_DATA_DIR=examples/demo COMPOSECAST_RELOAD=false PORT="$SERVER_PORT" \
  server-app/build/install/server-app/bin/server-app >"$WORK/server.log" 2>&1 &
SERVER_PID=$!

wait_for "http://127.0.0.1:$SERVER_PORT/health" 60
wait_for "http://127.0.0.1:$RENDERER_PORT/health" 120   # healthy = the warm-up render succeeded
curl -sf "http://127.0.0.1:$SERVER_PORT/screens/main" -o "$WORK/main.rc"

code=$(curl -s -o "$WORK/main.png" -w '%{http_code}' --data-binary @"$WORK/main.rc" "http://127.0.0.1:$RENDERER_PORT/render")
[[ "$code" == 200 ]] || { echo "render answered $code"; exit 1; }
size=$(python3 -I -c 'import struct,sys; d=open(sys.argv[1],"rb").read(24); assert d[:8]==b"\x89PNG\r\n\x1a\n", "not a PNG"; print("%dx%d" % struct.unpack(">II", d[16:24]))' "$WORK/main.png")
[[ "$size" == "1078x2399" ]] || { echo "unexpected PNG size $size"; exit 1; }

code=$(curl -s -o /dev/null -w '%{http_code}' --data-binary "not a document" "http://127.0.0.1:$RENDERER_PORT/render")
[[ "$code" == 400 ]] || { echo "garbage answered $code, expected 400"; exit 1; }

echo "preview renderer: OK (main rendered $size, garbage → 400)"
