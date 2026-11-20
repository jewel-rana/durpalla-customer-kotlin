#!/usr/bin/env bash
# Build debug APK (optional), install on connected device(s), launch, stream logcat.
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$(pwd)"
PKG="com.durpalla.booking"
ACTIVITY="$PKG/.MainActivity"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
SKIP_BUILD=0
NO_LOG=0
SERIAL_FILTER=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install-only) SKIP_BUILD=1; shift ;;
    --no-log) NO_LOG=1; shift ;;
    -s)
      [[ $# -ge 2 ]] || { echo "Missing serial after -s" >&2; exit 1; }
      SERIAL_FILTER="$2"
      shift 2
      ;;
    -h|--help)
      cat <<'EOF'
Usage: bash script/run-debug.sh [options]

  (default)     assembleDebug with full console, install -r -d, launch, logcat
  --install-only  skip Gradle; install existing debug APK
  --no-log        skip logcat after launch
  -s SERIAL       only this device (default: every authorized device)
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export PATH="${ANDROID_HOME}/platform-tools:${PATH}"

if [[ ! -f local.properties ]] && [[ -n "${ANDROID_HOME:-}" ]]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  echo "Wrote local.properties → sdk.dir=$ANDROID_HOME"
fi

ADB="$(command -v adb || true)"
if [[ -z "$ADB" ]]; then
  echo "adb not found. Install platform-tools or set ANDROID_HOME." >&2
  exit 1
fi

echo "==> Connected devices"
"$ADB" devices -l
mapfile -t SERIALS < <("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ ${#SERIALS[@]} -eq 0 ]]; then
  echo "No authorized devices/emulators (state=device)." >&2
  exit 1
fi

if [[ -n "$SERIAL_FILTER" ]]; then
  FOUND=0
  for s in "${SERIALS[@]}"; do
    if [[ "$s" == "$SERIAL_FILTER" ]]; then
      FOUND=1
      break
    fi
  done
  if [[ "$FOUND" -eq 0 ]]; then
    echo "Device not found or not authorized: $SERIAL_FILTER" >&2
    exit 1
  fi
  SERIALS=("$SERIAL_FILTER")
fi

echo "Targeting ${#SERIALS[@]} device(s): ${SERIALS[*]}"
echo

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "==> assembleDebug"
  ./gradlew --console=plain --info assembleDebug
  echo
fi

if [[ ! -f "$APK" ]]; then
  echo "Missing APK: $APK" >&2
  exit 1
fi
ls -lah "$APK"

for serial in "${SERIALS[@]}"; do
  echo
  echo "==> install -r -d on $serial"
  if ! "$ADB" -s "$serial" install -r -d "$APK"; then
    echo "Retry after uninstall..."
    "$ADB" -s "$serial" uninstall "$PKG" 2>/dev/null || true
    "$ADB" -s "$serial" install -r -d "$APK"
  fi
  "$ADB" -s "$serial" shell am force-stop "$PKG" 2>/dev/null || true
  "$ADB" -s "$serial" shell am start -n "$ACTIVITY"
  echo "==> Launched on $serial"
done

echo
echo "==> Done (pkg=$PKG)"

if [[ "$NO_LOG" -eq 0 ]]; then
  echo "==> logcat (Ctrl+C to stop)"
  if [[ ${#SERIALS[@]} -eq 1 ]]; then
    "$ADB" -s "${SERIALS[0]}" logcat --pid="$("$ADB" -s "${SERIALS[0]}" shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')" 2>/dev/null \
      || "$ADB" -s "${SERIALS[0]}" logcat "*:S" "Durpalla:V" "AndroidRuntime:E" "System.err:W"
  else
    echo "Multiple devices — pick one: adb -s SERIAL logcat"
    for serial in "${SERIALS[@]}"; do
      echo "  adb -s $serial logcat --pid=\$(adb -s $serial shell pidof -s $PKG)"
    done
  fi
fi
