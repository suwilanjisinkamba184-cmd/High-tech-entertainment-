#!/usr/bin/env bash
set -euo pipefail

repo_root="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$repo_root/hte-universe-native"

bash ../.github/scripts/wait-android-services.sh
# Compile the APK and test APK first. Gradle compilation can take long enough
# for a newly booted emulator to lose system services; verify readiness again
# immediately before attempting package installation and instrumentation.
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace --console=plain
bash ../.github/scripts/wait-android-services.sh
if gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace --console=plain; then
  echo "PASS: Android instrumentation tests completed."
else
  bash ../.github/scripts/capture-android-diagnostics.sh || true
  exit 1
fi
