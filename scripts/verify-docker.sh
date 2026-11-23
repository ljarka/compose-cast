#!/usr/bin/env bash
# Docker smoke test of the server image (T-009); run via `./scripts/verify.sh --docker` (CI job `docker`).
# Builds server-app/Dockerfile, starts it on examples/demo, waits for the healthcheck and checks
# /screens and /screens/main; then installs distribution/docker-compose.yml (T-023) the way users do
# against that image. Not part of the default verify run (keeps it fast and Docker-free).
set -euo pipefail
cd "$(dirname "$0")/.."

# Docker Desktop on macOS installs the CLI in ~/.docker/bin, which agent shells may not have on PATH.
command -v docker >/dev/null || export PATH="$HOME/.docker/bin:$PATH"
command -v docker >/dev/null || { echo "docker not found"; exit 1; }

IMAGE=compose-cast-server:verify
NAME="compose-cast-verify-$$"
PORT="${VERIFY_DOCKER_PORT:-18080}"

cleanup() {
  if [[ $? -ne 0 ]]; then echo "--- container logs"; docker logs "$NAME" 2>&1 | tail -40 || true; fi
  docker rm -f "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# The build stage uses the gradle image instead of the wrapper (see server-app/Dockerfile): keep
# its version equal to the wrapper's.
wrapper=$(sed -n 's/.*gradle-\([0-9.]*\)-bin.zip/\1/p' gradle/wrapper/gradle-wrapper.properties)
grep -q "^FROM .*gradle:${wrapper}-jdk21" server-app/Dockerfile || {
  echo "server-app/Dockerfile: build image must be gradle:${wrapper}-jdk21-… (wrapper is ${wrapper})"; exit 1; }

echo "Building $IMAGE"
docker build -q -f server-app/Dockerfile -t "$IMAGE" . >/dev/null

docker run -d --name "$NAME" -p "127.0.0.1:$PORT:8080" -v "$PWD/examples/demo:/data:ro" "$IMAGE" >/dev/null

echo "Waiting for the healthcheck (max 30 s)"
status=starting
for _ in $(seq 1 30); do
  status=$(docker inspect -f '{{.State.Health.Status}}' "$NAME")
  [[ "$status" == healthy ]] && break
  sleep 1
done
[[ "$status" == healthy ]] || { echo "Container not healthy after 30 s (status: $status)"; exit 1; }

expected_screens="["
first=true
for f in examples/demo/screens/*.json; do
  name=$(basename "$f" .json)
  $first || expected_screens+=","
  expected_screens+="\"$name\""
  first=false
done
expected_screens+="]"

screens=$(curl -fsS "http://127.0.0.1:$PORT/screens" | tr -d ' \n')
[[ "$screens" == "$expected_screens" ]] || { echo "Unexpected /screens: $screens (expected $expected_screens)"; exit 1; }
curl -fsS -o /dev/null "http://127.0.0.1:$PORT/screens/main"

# --- admin mode (T-007): a named volume (writable by the image's UID 1000), the env admin logs in ---
ADMIN_NAME="$NAME-admin"; ADMIN_VOLUME="$NAME-admin-data"; ADMIN_PORT="${VERIFY_DOCKER_ADMIN_PORT:-18082}"
admin_cleanup() { docker rm -f "$ADMIN_NAME" >/dev/null 2>&1 || true; docker volume rm "$ADMIN_VOLUME" >/dev/null 2>&1 || true; }
docker run -d --name "$ADMIN_NAME" -p "127.0.0.1:$ADMIN_PORT:8080" -v "$ADMIN_VOLUME:/data" \
  -e COMPOSECAST_MODE=admin -e COMPOSECAST_ADMIN_EMAIL=verify@example.com \
  -e COMPOSECAST_ADMIN_PASSWORD=verify-password-123 "$IMAGE" >/dev/null
for _ in $(seq 1 30); do curl -fsS -o /dev/null "http://127.0.0.1:$ADMIN_PORT/health" 2>/dev/null && break; sleep 1; done
login=$(curl -sS -o /dev/null -w '%{http_code}' -d email=verify@example.com -d password=verify-password-123 \
  "http://127.0.0.1:$ADMIN_PORT/admin/login")
[[ "$login" == 303 ]] || { echo "Admin login failed: HTTP $login"; docker logs "$ADMIN_NAME" 2>&1 | tail -20; admin_cleanup; exit 1; }
admin_cleanup
# A read-only data dir must stop admin mode with the fix in the message.
if out=$(docker run --rm -e COMPOSECAST_MODE=admin -v "$PWD/examples/demo:/data:ro" "$IMAGE" 2>&1); then
  echo "Admin mode started on a read-only /data"; exit 1
fi
grep -q "chown -R 1000:1000" <<<"$out" || { echo "Admin mode on read-only /data: unexpected error"; echo "$out" | tail -5; exit 1; }
echo "Admin mode: login OK, read-only /data refused"

size_mb=$(( $(docker run --rm --user root --entrypoint sh "$IMAGE" -c 'du -sxk /' | cut -f1) / 1024 ))
# A bloat guard, not a budget: raised from 250 for T-007 (sqlite-jdbc bundles natives for every OS,
# 12 MB; Freemarker 2 MB) at 251 MB. Raise it only for growth you can name.
echo "Image filesystem: ${size_mb} MB (limit 275)"
(( size_mb < 275 )) || { echo "Image too large"; exit 1; }

# --- distribution/docker-compose.yml (T-023): the install users run, against the image just built ---
# Installed like a user would (compose file + example.env as .env in an empty directory); the
# published image name is pointed at the local build with a tag no registry has.
docker compose version >/dev/null 2>&1 || { echo "docker compose (v2) not found"; exit 1; }
INSTALL=$(mktemp -d)
COMPOSE=(docker compose --progress quiet --project-directory "$INSTALL" -f "$INSTALL/docker-compose.yml" -p "compose-cast-verify-$$")
cleanup_install() {
  if [[ $? -ne 0 ]]; then echo "--- install logs"; "${COMPOSE[@]}" logs --tail 40 2>&1 || true; fi
  "${COMPOSE[@]}" down -v >/dev/null 2>&1 || true
  rm -rf "$INSTALL"
  cleanup
}
trap cleanup_install EXIT
docker rm -f "$NAME" >/dev/null 2>&1 || true

cp distribution/docker-compose.yml "$INSTALL/docker-compose.yml"
cp distribution/example.env "$INSTALL/.env"
cp -R examples/demo "$INSTALL/data"
docker tag "$IMAGE" "ghcr.io/ljarka/compose-cast-server:verify"
INSTALL_PORT="${VERIFY_DOCKER_INSTALL_PORT:-18081}"

# The HTTPS profile must at least be a valid configuration (certificates need a real domain).
COMPOSE_PROFILES=https COMPOSECAST_DOMAIN=app.example.com "${COMPOSE[@]}" config --quiet

echo "Starting distribution/docker-compose.yml"
COMPOSECAST_VERSION=verify COMPOSECAST_PORT="127.0.0.1:$INSTALL_PORT" \
  "${COMPOSE[@]}" up -d --wait --wait-timeout 60 --pull never >/dev/null
screens=$(curl -fsS "http://127.0.0.1:$INSTALL_PORT/screens" | tr -d ' \n')
[[ "$screens" == "$expected_screens" ]] || { echo "Unexpected /screens (install): $screens (expected $expected_screens)"; exit 1; }
docker rmi "ghcr.io/ljarka/compose-cast-server:verify" >/dev/null

echo "verify --docker: OK"
