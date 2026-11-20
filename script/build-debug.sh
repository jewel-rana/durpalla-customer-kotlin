#!/usr/bin/env bash
# Debug APK with full Gradle console output.
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$(pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"

if [[ ! -f local.properties ]] && [[ -n "${ANDROID_HOME:-}" ]]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  echo "Wrote local.properties → sdk.dir=$ANDROID_HOME"
fi

echo "==> Durpalla Customer (Kotlin) · debug build"
echo "    cwd: $ROOT"
echo

./gradlew --console=plain --info assembleDebug "$@"

echo
echo "==> Debug build finished"
if [[ -f "$APK" ]]; then
  ls -lah "$APK"
  echo "APK: $APK"
else
  echo "Expected APK not found: $APK" >&2
  exit 1
fi
