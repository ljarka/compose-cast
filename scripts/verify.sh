#!/usr/bin/env bash
# The single verification entry point. CI runs exactly this, so "green locally" == "green in CI".
#
#   ./scripts/verify.sh           full: docs, architecture, JVM build + tests, Android build + lint,
#                                 screenshot verification
#   ./scripts/verify.sh --quick   docs, architecture, JVM build + tests (no Android SDK needed);
#                                 enough as a baseline only for server-only work
#
# Exits non-zero on the first failing step and says what to look at.
set -euo pipefail
cd "$(dirname "$0")/.."

QUICK=false
[[ "${1:-}" == "--quick" ]] && QUICK=true

# Agent shells do not load ~/.zshrc; find JDK 21 if JAVA_HOME is not set (CI sets it).
if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in /opt/homebrew/opt/openjdk@21 /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/temurin-21-jdk-amd64; do
    if [[ -x "$candidate/bin/java" ]]; then export JAVA_HOME="$candidate"; break; fi
  done
fi
[[ -n "${JAVA_HOME:-}" ]] || { echo "JDK 21 not found: set JAVA_HOME"; exit 1; }

# Android SDK: local.properties is gitignored, so fresh checkouts/worktrees lack it.
if [[ ! -f local.properties && -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [[ -d "$candidate/platforms" ]]; then export ANDROID_HOME="$candidate"; break; fi
  done
fi

step() { echo; echo "=== $1"; }
gradle() { ./gradlew --console=plain "$@"; }

step "Docs consistency (ADR 0009)"
./scripts/check-docs.sh

step "Architecture rules"
./scripts/check-architecture.sh

step "JVM modules: build + tests"
gradle :protocol:build :sdk-server:build :server-scripting:build :server-app:build

if $QUICK; then
  echo; echo "verify --quick: OK (Android and screenshots skipped)"
  exit 0
fi

step "Android modules: build + lint"
gradle :sdk-android:assembleDebug :sdk-android:lintDebug :app-template:assembleDebug :app-template:lintDebug

step "Screenshot verification (ADR 0006)"
if ! gradle :compat-tests:verifyRoborazziDebug; then
  echo
  echo "Screenshots differ. Look at compat-tests/build/outputs/roborazzi/*_compare.png."
  echo "Intentional change? ./gradlew :compat-tests:recordRoborazziDebug, LOOK at the PNGs, commit them."
  exit 1
fi

echo; echo "verify: OK"
