#!/usr/bin/env bash
# Debug APK with full Gradle console output + auto-install on connected device(s).
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$(pwd)"
PKG="com.durpalla.booking"
ACTIVITY="$PKG/.MainActivity"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export PATH="${ANDROID_HOME}/platform-tools:${PATH}"

if [[ ! -f local.properties ]] && [[ -n "${ANDROID_HOME:-}" ]]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  echo "Wrote local.properties → sdk.dir=$ANDROID_HOME"
fi

echo "==> Durpalla Customer (Kotlin) · debug build"
echo "    cwd: $ROOT"
echo

./gradlew --console=plain --info assembleDebug -PskipGradleAutoInstall=true "$@"

echo
echo "==> Debug build finished"
if [[ -f "$APK" ]]; then
  ls -lah "$APK"
  echo "APK: $APK"
else
  echo "Expected APK not found: $APK" >&2
  exit 1
fi

ADB="$(command -v adb || true)"
if [[ -n "$ADB" ]]; then
  mapfile -t SERIALS < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#SERIALS[@]} -gt 0 ]]; then
    echo
    echo "==> Installing on ${#SERIALS[@]} connected device(s): ${SERIALS[*]}"
    for serial in "${SERIALS[@]}"; do
      echo "--> [$serial] adb install -r -d"
      if ! "$ADB" -s "$serial" install -r -d "$APK"; then
        echo "    Retrying after uninstall on $serial..."
        "$ADB" -s "$serial" uninstall "$PKG" 2>/dev/null || true
        "$ADB" -s "$serial" install -r -d "$APK"
      fi
      "$ADB" -s "$serial" shell am force-stop "$PKG" 2>/dev/null || true
      "$ADB" -s "$serial" shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
      echo "--> [$serial] Installed & launched $ACTIVITY"
    done
  else
    echo
    echo "==> No connected authorized adb devices found (skipping install)."
  fi
fi
