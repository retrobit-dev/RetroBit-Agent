#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner after the AVD is booted.
# Installs the release APK, launches MainActivity, grabs screenshots and fails
# loudly (via artifacts/smoke.log) if the app crashes or never reaches the front.
set -u

LOG=artifacts/smoke.log
mkdir -p artifacts
: > "$LOG"

log() { echo "$*" | tee -a "$LOG"; }

APK=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
log "apk=$APK"
if [ -z "${APK:-}" ]; then
  log "event=no-apk"
  exit 1
fi

log "== adb devices =="
adb devices 2>&1 | tee -a "$LOG"

log "== boot =="
adb wait-for-device 2>&1 | tee -a "$LOG"
for _ in $(seq 1 60); do
  BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BOOT" = "1" ] && break
  sleep 2
done
log "boot_completed=$BOOT"

log "== install =="
adb install -r "$APK" 2>&1 | tee -a "$LOG"
INSTALL=${PIPESTATUS[0]}
log "install_exit=$INSTALL"

log "== package =="
adb shell pm list packages 2>&1 | grep dev.retrobit.agent | tee -a "$LOG"

log "== launch =="
adb logcat -c 2>/dev/null
adb shell am start -W -n dev.retrobit.agent/.MainActivity 2>&1 | tee -a "$LOG"
LAUNCH=${PIPESTATUS[0]}

sleep 12
log "== foreground =="
adb shell dumpsys activity activities 2>/dev/null > /tmp/activities.txt
grep -E "mResumedActivity|topResumedActivity" /tmp/activities.txt | tee -a "$LOG"

log "== screenshots =="
adb exec-out screencap -p > artifacts/dashboard.png 2>>"$LOG"
adb shell input swipe 540 1500 540 600 300 2>/dev/null
sleep 2
adb exec-out screencap -p > artifacts/dashboard-scrolled.png 2>>"$LOG"

log "== crash buffer =="
adb logcat -d -b crash 2>/dev/null > artifacts/crash.log
cat artifacts/crash.log | tee -a "$LOG"
adb logcat -d 2>/dev/null | grep -E "AndroidRuntime|FATAL EXCEPTION" | head -40 >> artifacts/crash.log

FOREGROUND=0
grep -q "dev.retrobit.agent" /tmp/activities.txt && FOREGROUND=1
CRASH=0
grep -q "FATAL EXCEPTION" artifacts/crash.log && CRASH=1

log "install=$INSTALL launch=$LAUNCH foreground=$FOREGROUND crash=$CRASH"

if [ "$INSTALL" -ne 0 ] || [ "$LAUNCH" -ne 0 ] || [ "$FOREGROUND" -ne 1 ] || [ "$CRASH" -ne 1 ]; then
  log "== result: FAIL =="
  exit 1
fi
log "== result: OK =="
exit 0
