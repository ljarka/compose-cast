#!/usr/bin/env bash
# Is a newer Remote Compose release out than the one in gradle/libs.versions.toml?
# Exit 0 = up to date, 1 = newer release (or check failed). Needs network, so it is NOT part of
# verify.sh; CI runs it weekly (.github/workflows/rc-release-watch.yml).
# On a new release: read the release notes, then follow docs/playbooks/upgrade-remote-compose.md.
set -euo pipefail
cd "$(dirname "$0")/.."

NOTES=https://developer.android.com/jetpack/androidx/releases/compose-remote
ours=$(sed -nE 's/^remote-compose = "([^"]+)"/\1/p' gradle/libs.versions.toml)
latest=$(curl -sf https://dl.google.com/android/maven2/androidx/compose/remote/remote-core/maven-metadata.xml \
  | sed -nE 's:.*<latest>([^<]+)</latest>.*:\1:p')

if [[ -z "$latest" ]]; then echo "Could not read the latest Remote Compose version"; exit 1; fi
if [[ "$latest" == "$ours" ]]; then
  echo "Remote Compose $ours is the latest release."
  exit 0
fi
echo "New Remote Compose release: $latest (we use $ours)."
echo "Release notes: $NOTES"
echo "Upgrade: docs/playbooks/upgrade-remote-compose.md (alone, never with other dependencies)."
exit 1
