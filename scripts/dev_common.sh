#!/usr/bin/env bash
# Shared helpers for dev.sh / reload.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="com.durpalla.booking"
LAUNCHER=".MainActivity"
APK_DEBUG="${ROOT}/app/build/outputs/apk/debug/app-debug.apk"

SERIAL=""
NO_LOG=0

usage_common() {
  cat <<EOF
Options:
  -s SERIAL   Target a specific adb device
  --no-log    Skip logcat stream after launch
  -h, --help  Show help
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -s)
        SERIAL="${2:-}"
        shift 2
        ;;
      --no-log)
        NO_LOG=1
        shift
        ;;
      -h|--help)
        return 2
        ;;
      *)
        echo "Unknown option: $1" >&2
        return 2
        ;;
    esac
  done
}

require_adb() {
  if ! command -v adb >/dev/null 2>&1; then
    if [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
      export PATH="${ANDROID_HOME}/platform-tools:${PATH}"
    elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
      export PATH="${ANDROID_SDK_ROOT}/platform-tools:${PATH}"
    elif [[ -x /home/newroz/Android/Sdk/platform-tools/adb ]]; then
      export PATH="/home/newroz/Android/Sdk/platform-tools:${PATH}"
    else
      echo "adb not found. Install platform-tools or set ANDROID_HOME." >&2
      exit 1
    fi
  fi
}

resolve_device() {
  require_adb
  adb start-server >/dev/null 2>&1 || true
  if [[ -n "$SERIAL" ]]; then
    if ! adb -s "$SERIAL" get-state 2>/dev/null | grep -q device; then
      echo "Device not ready: $SERIAL" >&2
      adb devices -l >&2
      exit 1
    fi
    return
  fi
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#devices[@]} -eq 0 ]]; then
    echo "No adb device in 'device' state." >&2
    adb devices -l >&2
    mapfile -t other < <(adb devices | awk 'NR>1 && NF>=2 && $2!="device" {print $1 " (" $2 ")"}')
    if [[ ${#other[@]} -gt 0 ]]; then
      echo "Other adb entries (fix on phone: allow USB debugging / unplug-replug):" >&2
      printf '  %s\n' "${other[@]}" >&2
    else
      echo "Nothing listed by adb. Checklist:" >&2
      echo "  1) Physical phone: data USB cable, USB debugging ON, unlock phone, accept RSA prompt" >&2
      echo "  2) Confirm PC sees phone: lsusb  (should show a phone vendor, not empty of Android)" >&2
      echo "  3) Emulator: start AVD first, e.g. emulator -avd Durpalla_Phone &" >&2
      echo "  4) Wireless: adb pair IP:PORT then adb connect IP:PORT" >&2
    fi
    exit 1
  fi
  if [[ ${#devices[@]} -gt 1 ]]; then
    echo "Multiple devices found; using first. Pass -s SERIAL to pick one:" >&2
    printf '  %s\n' "${devices[@]}" >&2
  fi
  SERIAL="${devices[0]}"
}

adb_s() {
  adb -s "$SERIAL" "$@"
}

gradlew() {
  (cd "$ROOT" && ./gradlew "$@")
}

ensure_local_properties() {
  if [[ -f "${ROOT}/local.properties" ]]; then
    return
  fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk" && -d /home/newroz/Android/Sdk ]]; then
    sdk="/home/newroz/Android/Sdk"
  fi
  if [[ -z "$sdk" ]]; then
    echo "Missing local.properties and ANDROID_HOME / ANDROID_SDK_ROOT." >&2
    echo "Create local.properties with: sdk.dir=/path/to/Android/Sdk" >&2
    exit 1
  fi
  printf 'sdk.dir=%s\n' "$sdk" > "${ROOT}/local.properties"
  echo "==> wrote local.properties (sdk.dir=${sdk})"
}

launch_app() {
  adb_s shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb_s shell am start -n "${PKG}/${LAUNCHER}" >/dev/null
  echo "Launched ${PKG}/${LAUNCHER} on ${SERIAL}"
}

stream_logcat() {
  if [[ "$NO_LOG" -eq 1 ]]; then
    echo "Skipping logcat (--no-log)."
    return
  fi
  adb_s logcat -c || true
  echo "---- logcat stream (Ctrl+C to stop) device=${SERIAL} package=${PKG} ----"
  while true; do
    local pid=""
    local _i
    for _i in $(seq 1 40); do
      pid="$(adb_s shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
      [[ -n "$pid" ]] && break
      sleep 0.25
    done
    if [[ -z "$pid" ]]; then
      echo "Waiting for ${PKG}..."
      sleep 1
      continue
    fi
    echo "---- attached pid=${pid} ----"
    adb_s logcat --pid="$pid" '*:V' || true
  done
}
