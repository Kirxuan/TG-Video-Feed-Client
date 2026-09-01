#!/usr/bin/env sh
set -eu

: "${PKG:?PKG is required}"
: "${TEST_PKG:?TEST_PKG is required}"
: "${APK:?APK is required}"
: "${TEST_APK:?TEST_APK is required}"

ADB="${ADB:-adb}"

if [ -n "${ANDROID_SERIAL:-}" ]; then
  adb_cmd() {
    "$ADB" -s "$ANDROID_SERIAL" "$@"
  }
else
  adb_cmd() {
    "$ADB" "$@"
  }
fi

best_effort() {
  if ! "$@" 2>&1; then
    printf 'BEST_EFFORT_UNSUPPORTED:'
    printf ' %s' "$@"
    printf '\n'
  fi
}

if [ ! -f "$APK" ]; then
  printf 'Missing target APK: %s\n' "$APK" >&2
  exit 2
fi

if [ ! -f "$TEST_APK" ]; then
  printf 'Missing test APK: %s\n' "$TEST_APK" >&2
  exit 2
fi

device_state="$(adb_cmd get-state 2>/dev/null || true)"
if [ "$device_state" != "device" ]; then
  printf 'ADB device is not ready: %s\n' "$device_state" >&2
  exit 3
fi

printf 'VIVO_TEST_PREP_BEGIN pkg=%s testPkg=%s\n' "$PKG" "$TEST_PKG"

adb_cmd shell svc power stayon usb
adb_cmd shell settings put global stay_on_while_plugged_in 3
adb_cmd shell settings put global window_animation_scale 0
adb_cmd shell settings put global transition_animation_scale 0
adb_cmd shell settings put global animator_duration_scale 0
best_effort adb_cmd shell settings put global verifier_verify_adb_installs 0
best_effort adb_cmd shell settings put secure install_non_market_apps 1

best_effort adb_cmd shell pm install-existing --user 0 "$PKG"
best_effort adb_cmd shell pm install-existing --user 0 "$TEST_PKG"

adb_cmd install -r -t -g "$APK"
adb_cmd install -r -t -g "$TEST_APK"

for package_name in "$PKG" "$TEST_PKG"; do
  best_effort adb_cmd shell dumpsys deviceidle whitelist +"$package_name"
  best_effort adb_cmd shell cmd appops set "$package_name" RUN_IN_BACKGROUND allow
  best_effort adb_cmd shell cmd appops set "$package_name" RUN_ANY_IN_BACKGROUND allow
  best_effort adb_cmd shell pm set-app-restricted "$package_name" false
  best_effort adb_cmd shell cmd package set-bg-restriction "$package_name" 0
  best_effort adb_cmd shell cmd activity set-bg-restriction-level --user 0 \
    "$package_name" unrestricted
  best_effort adb_cmd shell cmd activity set-standby-bucket --user 0 \
    "$package_name" active
  best_effort adb_cmd shell cmd activity unfreeze --sticky "$package_name"
done

adb_cmd shell input keyevent KEYCODE_WAKEUP
best_effort adb_cmd shell wm dismiss-keyguard
adb_cmd shell am force-stop "$PKG"
adb_cmd shell am force-stop "$TEST_PKG"

printf 'VIVO_TEST_PREP_STATE\n'
for package_name in "$PKG" "$TEST_PKG"; do
  printf '%s bg=' "$package_name"
  best_effort adb_cmd shell cmd activity get-bg-restriction-level --user 0 "$package_name"
  printf '%s bucket=' "$package_name"
  best_effort adb_cmd shell cmd activity get-standby-bucket --user 0 "$package_name"
done

printf 'VIVO_TEST_PREP_DONE\n'
