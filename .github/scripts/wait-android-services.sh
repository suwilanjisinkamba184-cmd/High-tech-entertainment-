#!/usr/bin/env bash
set -euo pipefail

# The emulator action may report a booted device before PackageManager and
# ActivityManager are registered. Wait for the actual services used by APK
# installation and Android instrumentation, not only sys.boot_completed.
diagnostics_dir="${GITHUB_WORKSPACE:-$(pwd)}/hte-universe-native/build/diagnostics"
mkdir -p "$diagnostics_dir"
services_ready=false

for attempt in $(seq 1 90); do
  device_state="$(adb get-state 2>/dev/null || true)"
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  package_list="$(adb shell pm list packages 2>/dev/null || true)"
  current_user="$(adb shell am get-current-user 2>/dev/null || true)"
  package_service="$(adb shell service check package 2>/dev/null || true)"
  activity_service="$(adb shell service check activity 2>/dev/null || true)"

  if [[ "$device_state" == "device" &&
        "$boot_completed" == "1" &&
        -n "$package_list" &&
        "$current_user" =~ ^[0-9]+$ &&
        "$package_service" == *"found"* &&
        "$activity_service" == *"found"* ]]; then
    services_ready=true
    break
  fi
  sleep 2
done

if [[ "$services_ready" != "true" ]]; then
  bash ../.github/scripts/capture-android-diagnostics.sh || true
  echo "::error::Android emulator did not expose working package/activity services after 180 seconds."
  exit 1
fi

echo "PASS: Android emulator booted; PackageManager and ActivityManager are available."
