#!/usr/bin/env bash
# Incremental installDebug → relaunch → filtered logcat (connected device)
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/dev_common.sh
source "${SCRIPT_DIR}/scripts/dev_common.sh"

if ! parse_args "$@"; then
  echo "Usage: ./reload.sh [-s SERIAL] [--no-log]"
  usage_common
  exit 0
fi

ensure_local_properties
resolve_device
echo "==> Device: ${SERIAL}"
echo "==> installDebug"
gradlew :app:installDebug
launch_app
stream_logcat
