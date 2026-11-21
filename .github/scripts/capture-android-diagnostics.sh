#!/usr/bin/env bash
set -euo pipefail

# Run from hte-universe-native while the emulator is still alive.
diagnostics_dir="${GITHUB_WORKSPACE:-$(pwd)}/hte-universe-native/build/diagnostics"
mkdir -p "$diagnostics_dir"
{
  echo "=== Timestamp ==="
  date -u
  echo
  echo "=== adb devices ==="
  adb devices -l || true
  echo
  echo "=== Device state ==="
  adb get-state || true
  echo
  echo "=== Boot properties ==="
  adb shell getprop sys.boot_completed || true
  adb shell getprop ro.build.version.sdk || true
  adb shell getprop ro.boot.qemu || true
  echo
  echo "=== Package and activity services ==="
  adb shell service check package || true
  adb shell service check activity || true
  adb shell pm list packages || true
  adb shell am get-current-user || true
  echo
  echo "=== Recent logcat ==="
  adb logcat -d -t 5000 || true
} > "$diagnostics_dir/emulator-diagnostics.txt" 2>&1

echo "Emulator diagnostics written to $diagnostics_dir/emulator-diagnostics.txt"
