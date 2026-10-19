#!/usr/bin/env bash
# Deep UI walk-through on a booted emulator. Called by scripts/smoke.sh.
# Taps real Compose nodes (found via uiautomator) and screenshots each step so a
# runtime regression shows up as a broken image instead of a silent success.
set -u

LOG=${LOG:-artifacts/smoke.log}
ADB=${ADB:-adb}
export LOG

log() { echo "$*" | tee -a "$LOG" >/dev/null; }
note() { echo "$*" >&2; log "$*"; }

shot() {
  local name="$1"
  $ADB exec-out screencap -p > "artifacts/$name.png" 2>>"$LOG" || true
  log "shot=$name"
}

dump_ui() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB shell cat /sdcard/ui.xml > /tmp/ui.xml 2>/dev/null
  [ -s /tmp/ui.xml ]
}

# tap_text <substring>  — taps the centre of the first node whose text or
# content-desc contains the substring (case-insensitive).
tap_text() {
  local needle="$1"
  local xy
  for attempt in 1 2 3; do
    dump_ui || { sleep 1; continue; }
    xy=$(python3 - "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[1].lower()
try:
    root = ET.parse("/tmp/ui.xml").getroot()
except Exception:
    sys.exit(1)
for node in root.iter("node"):
    label = ((node.get("text") or "") + " " + (node.get("content-desc") or "")).strip()
    if needle in label.lower():
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
)
    [ -n "${xy:-}" ] && break
    sleep 1
  done
  if [ -z "${xy:-}" ]; then
    log "event=tap-miss text=\"$needle\""
    return 1
  fi
  # shellcheck disable=SC2086
  $ADB shell input tap $xy >>"$LOG" 2>&1
  log "event=tap text=\"$needle\" at=$xy"
  sleep 1.5
  return 0
}

crash_check() {
  $ADB logcat -d -b crash 2>/dev/null > artifacts/crash.log
  if grep -q "FATAL EXCEPTION" artifacts/crash.log 2>/dev/null; then
    log "event=crash phase=$1"
    tail -40 artifacts/crash.log >> "$LOG"
    return 1
  fi
  return 0
}

# ---------------------------------------------------------------- walk-through
FAILED=0
UI_ISSUES=0

shot 01-dashboard

if tap_text "Nimbus"; then
  sleep 3
  shot 02-workspace-chat
  crash_check workspace || FAILED=1
else
  UI_ISSUES=$((UI_ISSUES + 1))
fi

for tab in "Preview:03-preview" "Kode:04-code" "Desain:05-design" "Data:06-data" "Vault:07-vault" "Chat:08-chat"; do
  label=${tab%%:*}
  name=${tab##*:}
  if tap_text "$label"; then
    sleep 3
    shot "$name"
    crash_check "tab-$label" || FAILED=1
  else
    UI_ISSUES=$((UI_ISSUES + 1))
  fi
done

# Send a demo prompt and wait for the agent to finish writing files.
if tap_text "Minta perubahan"; then
  $ADB shell input text "buat%saplikasi%spencatat%stugas%smingguan" >>"$LOG" 2>&1
  sleep 1
  if tap_text "Kirim"; then
    log "event=prompt-sent"
    for _ in $(seq 1 12); do
      sleep 5
      crash_check prompt || { FAILED=1; break; }
    done
    shot 09-after-prompt
  else
    UI_ISSUES=$((UI_ISSUES + 1))
  fi
else
  UI_ISSUES=$((UI_ISSUES + 1))
fi

# Back to the dashboard.
$ADB shell input keyevent KEYCODE_BACK >>"$LOG" 2>&1
sleep 2
shot 10-back-to-dashboard

log "ui_issues=$UI_ISSUES"
log "failed=$FAILED"
[ "$FAILED" -eq 0 ] || exit 1
exit 0
