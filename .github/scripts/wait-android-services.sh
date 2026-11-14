#!/usr/bin/env bash
set -euo pipefail

# The emulator-runner executes each line of its script as a separate shell
# command. Keep loops inside this checked-in Bash script instead.
services_ready=false
for attempt in $(seq 1 30); do
  if adb shell pm list packages >/dev/null 2>&1 && adb shell am get-current-user >/dev/null 2>&1; then
    services_ready=true
    break
  fi
  sleep 2
done

if [[ "$services_ready" != "true" ]]; then
  adb devices -l || true
  adb logcat -d -t 1500 || true
  echo "::error::Android package/activity services did not become ready."
  exit 1
fi

echo "PASS: Android package and activity services are ready."
