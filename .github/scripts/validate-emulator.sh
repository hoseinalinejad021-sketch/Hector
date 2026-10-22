#!/usr/bin/env bash
set -euo pipefail

apk="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$apk" ]]; then
  echo "::error::Debug APK is missing or empty: $apk"
  find app/build/outputs -maxdepth 5 -type f -name '*.apk' -print 2>/dev/null || true
  exit 1
fi

adb install -r "$apk"
adb shell pm list packages | grep -q 'package:com.hector.android'
adb shell am start -W -n com.hector.android/.MainActivity

attempt=0
while [[ "$attempt" -lt 15 ]]; do
  if adb shell pidof com.hector.android >/dev/null 2>&1; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 2
done

if ! adb shell pidof com.hector.android; then
  echo "::error::Hector process did not remain running after launch."
  adb logcat -d -t 500 | grep -iE 'FATAL EXCEPTION|AndroidRuntime|com.hector.android' | tail -n 100 || true
  exit 1
fi

adb shell am force-stop com.hector.android
echo "Emulator install and launch validation passed."
