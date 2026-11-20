#!/usr/bin/env bash
# Full debug build → install → launch → filtered logcat (connected device)
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/dev_common.sh
source "${SCRIPT_DIR}/scripts/dev_common.sh"

if ! parse_args "$@"; then
  echo "Usage: ./dev.sh [-s SERIAL] [--no-log]"
  usage_common
  exit 0
fi

ensure_local_properties
resolve_device
echo "==> Device: ${SERIAL}"
echo "==> assembleDebug"
gradlew :app:assembleDebug
if [[ ! -f "$APK_DEBUG" ]]; then
  echo "Missing APK: $APK_DEBUG" >&2
  exit 1
fi
echo "==> install -r -d (replace; allow debug downgrade if needed)"
adb_s install -r -d "$APK_DEBUG"
launch_app
stream_logcat
