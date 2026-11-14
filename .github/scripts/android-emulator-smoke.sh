#!/usr/bin/env bash
set -euo pipefail

API_LEVEL="${1:?Android API level is required}"
APP_ID="com.hightechentertainment.app.dev"
APK="app/build/outputs/apk/debug/app-debug.apk"

adb wait-for-device
booted=false
for attempt in $(seq 1 120); do
  state="$(adb get-state 2>/dev/null || true)"
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$state" == "device" && "$boot_completed" == "1" ]]; then
    booted=true
    break
  fi
  sleep 5
done
if [[ "$booted" != "true" ]]; then
  adb devices -l
  echo "::error::Android emulator did not reach a stable online and boot-complete state."
  exit 1
fi

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell pm list packages >/dev/null

cd hte-universe-native
if (( API_LEVEL <= 26 )); then
  gradle --no-daemon :app:assembleDebug --console=plain
  test -s "$APK"
  installed=false
  for attempt in 1 2 3; do
    if adb install --no-streaming -r "$APK"; then
      installed=true
      break
    fi
    adb wait-for-device
    sleep 5
  done
  if [[ "$installed" != "true" ]]; then
    adb devices -l
    adb logcat -d -t 1500
    echo "::error::Android API ${API_LEVEL} could not install the debug APK after three attempts."
    exit 1
  fi
  adb logcat -c
  adb shell am force-stop "$APP_ID"
  adb shell am start -W -n "$APP_ID/.MainActivity"
  sleep 5
  adb shell dumpsys activity activities | grep -F "$APP_ID/.MainActivity"
  if adb logcat -d -t 2500 | grep -E "FATAL EXCEPTION|Process: $APP_ID"; then
    echo "::error::HTE Universe crashed during the Android API ${API_LEVEL} launch smoke test."
    exit 1
  fi
  echo "PASS: Android API ${API_LEVEL} installed HTE Universe and launched MainActivity."
else
  gradle --no-daemon :app:connectedDebugAndroidTest --console=plain
fi
