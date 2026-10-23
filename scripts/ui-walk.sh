#!/usr/bin/env bash
# Deep UI walk-through on a booted emulator. Called by scripts/smoke.sh.
# Taps real Compose nodes (found via uiautomator) and screenshots each step so a
# runtime regression shows up as a broken image instead of a silent success.
set -u

LOG=${LOG:-artifacts/smoke.log}
ADB=${ADB:-adb}
APP_ID=${APP_ID:-dev.retrobit.agent}
export LOG

log() { echo "$*" | tee -a "$LOG" >/dev/null; }

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

# find_xy <substring>  -> prints "x y" of the first matching node
find_xy() {
  python3 - "$1" "${2:-loose}" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[1].lower()
try:
    root = ET.parse("/tmp/ui.xml").getroot()
except Exception:
    sys.exit(1)
exact, loose = [], []
for node in root.iter("node"):
    label = ((node.get("text") or "") + " " + (node.get("content-desc") or "")).strip()
    low = label.lower()
    # A floating label can lie outside the input's hit region. For form edits,
    # tap the enclosing editable node, not the decorative label's coordinates.
    if sys.argv[2] == "field":
        if node.get("class") != "android.widget.EditText":
            continue
        if not any(needle in ((n.get("text") or "") + " " + (n.get("content-desc") or "")).lower() for n in node.iter("node")):
            continue
        low = needle
    if needle in low:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        point = ((x1 + x2) // 2, (y1 + y2) // 2)
        # An exact label wins over a substring: "Chat" must be the tab, not the
        # "chat baru" pill that a scrolled page happens to show.
        (exact if low == needle else loose).append(point)
if exact:
    print(*exact[0]); sys.exit(0)
if loose and sys.argv[2] != "exact":
    print(*loose[0]); sys.exit(0)
sys.exit(1)
PY
}

# tap_text <substring> — taps the centre of the first matching node.
tap_text() {
  local needle="$1" xy=""
  for _ in 1 2 3; do
    if dump_ui; then
      xy=$(find_xy "$needle") && break
    fi
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

# tap_text_scrolling <substring> — like tap_text, but scrolls the page down
# until the node exists (the dashboard is taller than the screen).
tap_text_scrolling() {
  local needle="$1" mode="${2:-loose}" xy=""
  for _ in 1 2 3 4 5 6; do
    if dump_ui; then
      xy=$(find_xy "$needle" "$mode") && break
    fi
    $ADB shell input swipe 540 1400 540 900 250 >>"$LOG" 2>&1
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

# Accessibility gate. Material expands many small icons to a 48 dp touch
# target, but any custom clickable box does not, so measure what the device
# actually exposes and report the offenders.
touch_target_check() {
  local phase="$1"
  dump_ui || return 0
  local density size_w size_h
  density=$($ADB shell wm density 2>/dev/null | sed -n 's/.*: *//p' | head -1)
  [ -n "$density" ] || density=420
  set -- $($ADB shell wm size 2>/dev/null | tail -1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
  size_w=${1:-1080}
  size_h=${2:-1920}
  python3 - "$phase" "$density" "$size_w" "$size_h" <<'PY2' >>"$LOG" 2>&1
import re, sys, xml.etree.ElementTree as ET
phase, density = sys.argv[1], int(sys.argv[2])
screen_w, screen_h = int(sys.argv[3]), int(sys.argv[4])
minimum = max(48, density * 48 // 160)
try:
    root = ET.parse("/tmp/ui.xml").getroot()
except Exception:
    sys.exit(0)
# Top of the app's bottom tab bar: the clickable item that wraps a tab label.
# Anything below that line is covered by the bar, and uiautomator only reports
# the uncovered slice — which made a 48 dp chip look like 26 dp.
density_px = density / 160.0
bar_top = screen_h - int(200 * density_px)
label = None
for node in root.iter("node"):
    if (node.get("text") or "").strip() in ("Beranda", "Chat", "Template", "Cloud"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            label = ((x1 + x2) // 2, (y1 + y2) // 2)
            break
if label is not None:
    for node in root.iter("node"):
        if node.get("clickable") != "true":
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        if x1 <= label[0] <= x2 and y1 <= label[1] <= y2:
            bar_top = y1
            break
small = []
for node in root.iter("node"):
    if node.get("clickable") != "true":
        continue
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    # A node clipped by any screen edge is not a small target, just half visible.
    if y2 >= screen_h - 2 or x2 >= screen_w - 2 or y1 <= 2 or x1 <= 2:
        continue
    # Nor is one that the bottom tab bar covers: uiautomator reports the clipped
    # height, which made a 48 dp chip look like 26 dp whenever a fling happened
    # to stop with the row half under the bar.
    # 16 dp of tolerance: the bar's top edge and a clipped node's bottom edge
    # land within a few pixels of each other, and an 8 dp overlap is invisible.
    if y2 > bar_top - int(16 * density / 160.0):
        continue
    w, h = x2 - x1, y2 - y1
    # Anything thinner than 8 dp is the visible sliver of a card that is half
    # scrolled off the fold, not a control somebody has to hit.
    if w < minimum // 6 or h < minimum // 6:
        continue
    if w >= minimum and h >= minimum:
        continue
    label = (node.get("content-desc") or node.get("text") or node.get("resource-id") or "?").strip()
    dp = lambda px: round(px * 160 / density)
    small.append(f"{label[:24]}({dp(w)}x{dp(h)}dp@{x1},{y1})")
print(f"event=touch-targets phase={phase} min_px={minimum} small={len(small)}")
if small:
    print("event=touch-target-small " + " ".join(small[:12]))
PY2
  rm -f /tmp/ui.xml
}

# Only the app's own process counts. The emulator's UiAutomation service also
# writes "FATAL EXCEPTION: UiAutomation ... Bad file descriptor" to the crash
# buffer when the instrumentation loses its binder, and that used to fail the
# whole walk even though the app never crashed.
crash_check() {
  $ADB logcat -d -b crash 2>/dev/null > artifacts/crash.log
  if ! grep -q "FATAL EXCEPTION" artifacts/crash.log 2>/dev/null; then
    return 0
  fi
  if grep -qE "Process: ${APP_ID}(,|$)" artifacts/crash.log 2>/dev/null; then
    log "event=crash phase=$1"
    tail -40 artifacts/crash.log >> "$LOG"
    return 1
  fi
  # Something else died; record it so a real crash pattern is still visible.
  local foreign
  foreign=$(grep -m1 "FATAL EXCEPTION" artifacts/crash.log | tr -d '\r')
  log "event=crash-foreign phase=$1 what=${foreign#[[:space:]]}"
  return 0
}

UI_ISSUES=0
FAILED=0

# Make sure the dashboard is scrolled to the top: its header carries the drawer
# button and the hero headline that the screenshots are meant to show.
$ADB shell input swipe 540 700 540 1600 300 >>"$LOG" 2>&1
sleep 1
$ADB shell input swipe 540 700 540 1600 300 >>"$LOG" 2>&1
sleep 1
shot 01-dashboard
touch_target_check dashboard

if tap_text_scrolling "Nimbus"; then
  sleep 3
  shot 02-workspace-chat
  if tap_text "Menu" && tap_text "Tool agen"; then
    shot 02a-toolbox
    if ! tap_text "Tutup tool"; then UI_ISSUES=$((UI_ISSUES + 1)); fi
  else
    UI_ISSUES=$((UI_ISSUES + 1))
  fi
  touch_target_check workspace
  crash_check workspace || FAILED=1
else
  UI_ISSUES=$((UI_ISSUES + 1))
fi

for pair in "Preview:03-preview" "Kode:04-code" "Desain:05-design" "Data:06-data" "Vault:07-vault" "Chat:08-chat"; do
  label=${pair%%:*}
  name=${pair##*:}
  if [ "$label" = "Preview" ]; then $ADB logcat -c 2>/dev/null; fi
  if tap_text "$label"; then
    sleep 3
    shot "$name"
    if [ "$label" = "Preview" ]; then
      # Probe runs in the loaded virtual-origin document after runtime injection.
      # Checking only a Compose label or a workflow exit code is insufficient.
      for _ in 1 2 3 4 5 6; do
        $ADB logcat -d -s RetroBitPreview:I '*:S' > artifacts/preview-readiness.log 2>/dev/null
        if grep -q "ready virtual-origin bridge=1 content=1" artifacts/preview-readiness.log; then break; fi
        sleep 1
      done
      if grep -q "ready virtual-origin bridge=1 content=1" artifacts/preview-readiness.log; then
        log "event=preview-virtual-origin-rendered"
      else
        dump_ui && cp /tmp/ui.xml artifacts/preview-ui.xml
        log "event=preview-content-or-bridge-missing"
        UI_ISSUES=$((UI_ISSUES + 1)); FAILED=1
      fi
    fi
    crash_check "tab-$label" || FAILED=1
  else
    UI_ISSUES=$((UI_ISSUES + 1))
  fi
done

# --- type a prompt -----------------------------------------------------------
SEND_XY=""
if dump_ui; then
  SEND_XY=$(find_xy "Kirim" || true)
fi

if [ -n "$SEND_XY" ]; then
  SX=$(echo "$SEND_XY" | cut -d' ' -f1)
  SY=$(echo "$SEND_XY" | cut -d' ' -f2)
  $ADB shell input tap $((SX - 300)) "$SY" >>"$LOG" 2>&1
  log "event=tap-composer at=$((SX - 300)),$SY"
else
  SIZE=$($ADB shell wm size | tail -1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
  W=$(echo "$SIZE" | cut -d' ' -f1)
  H=$(echo "$SIZE" | cut -d' ' -f2)
  $ADB shell input tap $((W / 2)) $((H * 70 / 100)) >>"$LOG" 2>&1
  log "event=tap-composer-fallback"
fi
sleep 2

$ADB shell input text "buat%saplikasi%spencatat%stugas%smingguan" >>"$LOG" 2>&1
sleep 2
shot 09-prompt-typed

if dump_ui && grep -qi "pencatat" /tmp/ui.xml; then
  log "event=prompt-typed"
else
  log "event=prompt-typing-failed"
  UI_ISSUES=$((UI_ISSUES + 1))
fi

if tap_text "Kirim"; then
  log "event=prompt-sent"
  for _ in $(seq 1 10); do
    sleep 5
    crash_check prompt || { FAILED=1; break; }
  done
  sleep 6
  shot 09-after-prompt
  if dump_ui && grep -qi "pencatat" /tmp/ui.xml; then
    log "event=prompt-in-transcript"
  else
    log "event=prompt-missing-from-transcript"
    UI_ISSUES=$((UI_ISSUES + 1))
  fi
else
  UI_ISSUES=$((UI_ISSUES + 1))
fi

# --- dashboard drawer --------------------------------------------------------
# The soft keyboard may still be up, so BACK once closes it and only the second
# press leaves the workspace. Keep pressing until the dashboard is visible.
for _ in 1 2 3; do
  $ADB shell input keyevent KEYCODE_BACK >>"$LOG" 2>&1
  sleep 2
  if dump_ui && grep -qi "Terbaru" /tmp/ui.xml; then
    break
  fi
done
# The header (with the drawer button) only exists at the very top of the list.
for _ in 1 2 3; do
  $ADB shell input swipe 540 700 540 1600 250 >>"$LOG" 2>&1
  sleep 1
done
shot 10-back-to-dashboard

if tap_text "Menu"; then
  sleep 2
  shot 11-drawer
  if tap_text "Inspeksi APK"; then
    shot 11a-apk-inspector
    if tap_text "Analisis APK RetroBit"; then
      APK_READY=0
      for _ in 1 2 3 4 5 6 7 8 9 10; do
        dump_ui || true
        if grep -q "Salinan sementara dihapus" /tmp/ui.xml; then APK_READY=1; break; fi
        sleep 2
      done
      if [ "$APK_READY" = "1" ]; then
        for _ in 1 2 3; do
          dump_ui || true
          if grep -q "SHA-256 APK" /tmp/ui.xml && grep -q "dev.retrobit.agent" /tmp/ui.xml; then break; fi
          $ADB shell input swipe 540 1300 540 900 500 >>"$LOG" 2>&1
        done
        shot 11b-apk-report
        if grep -q "SHA-256 APK" /tmp/ui.xml && grep -q "dev.retrobit.agent" /tmp/ui.xml; then
          log "event=apk-inspector-real-package"
        else
          log "event=apk-inspector-report-missing"; FAILED=1
        fi
        if tap_text "Sertifikat"; then
          dump_ui || true
          shot 11c-apk-certificate
          if grep -q "SHA-256 sertifikat" /tmp/ui.xml && grep -q "Signer saat ini" /tmp/ui.xml; then
            log "event=apk-inspector-certificate"
          else
            log "event=apk-inspector-certificate-missing"; FAILED=1
          fi
        else FAILED=1; fi
      else
        log "event=apk-inspector-not-ready"; FAILED=1
      fi
    else FAILED=1; fi
    crash_check apk-inspector || FAILED=1
    if ! tap_text "Menu"; then FAILED=1; fi
  else FAILED=1; fi
  crash_check drawer || FAILED=1
  if tap_text "AI Cloud"; then
    sleep 3
    shot 12-cloud
    crash_check cloud || FAILED=1
    # On-device startup smoke: load the actual JNI library from the release APK,
    # without a model/network. Do not mistake this for an inference benchmark.
    if tap_text_scrolling "AI di HP (offline)"; then
      if tap_text_scrolling "Periksa mesin"; then
        sleep 2
        dump_ui || true
        if grep -q "Mesin CPU siap" /tmp/ui.xml && grep -q "Grammar tool valid" /tmp/ui.xml; then
          log "event=native-backend-ready"
        else
          log "event=native-backend-failed"
          FAILED=1
        fi
        $ADB shell input swipe 540 1300 540 900 1200 >>"$LOG" 2>&1
        sleep 1
        shot 12aa-ondevice
        touch_target_check ondevice
        # A separate screenshot proves the download/import controls, without
        # fetching model weights or spending anyone's data allowance in smoke.
        for _ in 1 2 3; do
          dump_ui || true
          DOWNLOAD=$(find_xy "Download & impor GGUF" || true)
          if [ -n "$DOWNLOAD" ]; then break; fi
          $ADB shell input swipe 540 1300 540 900 1200 >>"$LOG" 2>&1
        done
        if [ -z "${DOWNLOAD:-}" ]; then log "event=missing download-model button"; FAILED=1; fi
        shot 12ab-model-download
        crash_check ondevice || FAILED=1
      else
        FAILED=1
      fi
      # Reset scroll before the existing Ollama step.
      for _ in 1 2 3 4 5; do
        $ADB shell input swipe 540 700 540 1600 300 >>"$LOG" 2>&1
      done
      sleep 1
    else
      FAILED=1
    fi
    # The local-model panel only exists once Ollama is picked: its hint and
    # detect button are the whole point of the unlimited path, so photograph it.
    if tap_text "Ollama / lokal"; then
      sleep 2
      # The credentials panel opens below the grid, so the hint and the detect
      # button are off-screen until the page moves.
      $ADB shell input swipe 540 1300 540 800 1200 >>"$LOG" 2>&1
      sleep 2
      shot 12a-cloud-ollama
      crash_check cloud-ollama || FAILED=1
      if ! grep -qi "deteksi model" <<<"$(dump_ui && cat /tmp/ui.xml)"; then
        log "event=missing detect-model button"
        FAILED=1
      fi
      # Back to the top of the list before scrolling for the next section.
      $ADB shell input swipe 540 700 540 1600 300 >>"$LOG" 2>&1
      $ADB shell input swipe 540 700 540 1600 300 >>"$LOG" 2>&1
      sleep 1
    fi
    # The unlimited/keys section sits below the provider grid, so the walk has
    # to scroll for it — the top screenshot alone would never prove it renders.
    # Slow swipes instead of flings: a fling stops wherever momentum takes it,
    # and a row that lands half under the tab bar is unmeasurable.
    SECTION_Y=""
    for _ in 1 2 3 4 5; do
      dump_ui || true
      SECTION=$(find_xy "Tanpa batas" || true)
      if [ -n "$SECTION" ]; then
        SECTION_Y=${SECTION#* }
        if [ "$SECTION_Y" -gt 200 ] && [ "$SECTION_Y" -lt 900 ]; then break; fi
      fi
      $ADB shell input swipe 540 1300 540 900 1200 >>"$LOG" 2>&1
      sleep 1
    done
    log "event=scrolled-to cloud-unlimited y=${SECTION_Y:-unknown}"
    sleep 1
    shot 12b-cloud-unlimited
    touch_target_check cloud-scrolled
    crash_check cloud-scrolled || FAILED=1
  else
    UI_ISSUES=$((UI_ISSUES + 1))
  fi
else
  UI_ISSUES=$((UI_ISSUES + 1))
fi

# --- plain chat mode ---------------------------------------------------------
# Reached from the tab bar that is visible on every main screen, so no extra
# back-navigation is needed after the Cloud screenshot.
if tap_text "Chat"; then
  sleep 2
  shot 13-chat-list
  crash_check chat-list || FAILED=1
  if tap_text "Obrolan baru"; then
    sleep 1
    shot 14-chat-thread
    # The composer placeholder sits behind an empty BasicTextField, so it never
    # shows up in the uiautomator dump. Aim relative to the send button instead.
    CHAT_SEND=""
    if dump_ui; then
      CHAT_SEND=$(find_xy "Kirim" || true)
    fi
    if [ -n "$CHAT_SEND" ]; then
      CX=$(echo "$CHAT_SEND" | cut -d' ' -f1)
      CY=$(echo "$CHAT_SEND" | cut -d' ' -f2)
      $ADB shell input tap $((CX - 300)) "$CY" >>"$LOG" 2>&1
      log "event=tap-chat-composer at=$((CX - 300)),$CY"
      sleep 1
      $ADB shell input text "tes%smode%schat" >>"$LOG" 2>&1
      sleep 1
      if tap_text "Kirim"; then
        sleep 3
        shot 15-chat-reply
        if dump_ui && grep -qiE "Belum ada model GGUF" /tmp/ui.xml; then
          log "event=chat-needs-real-model-no-simulation"
        else
          log "event=chat-reply-missing"
          UI_ISSUES=$((UI_ISSUES + 1))
        fi
      else
        UI_ISSUES=$((UI_ISSUES + 1))
      fi
    else
      log "event=chat-composer-missing"
      UI_ISSUES=$((UI_ISSUES + 1))
    fi
  else
    UI_ISSUES=$((UI_ISSUES + 1))
  fi
else
  UI_ISSUES=$((UI_ISSUES + 1))
fi

# --- Actual production HTTPS download -> private model -> JNI generation ----
# The default user path, not a fixture copied into private app storage. A host
# reference was independently verified against immutable publisher LFS metadata.
$ADB shell input keyevent KEYCODE_BACK >>"$LOG" 2>&1
sleep 2
MODEL_URL=$(python3 -c 'import json; m=json.load(open("artifacts-local/ci-model.json")); print("https://huggingface.co/"+m["repository"]+"/resolve/"+m["revision"]+"/"+m["filename"])')
EXPECTED_SHA=$(python3 -c 'import json; print(json.load(open("artifacts-local/ci-model.json"))["sha256"])')
if tap_text "Cloud"; then
  for _ in 1 2 3 4 5; do $ADB shell input swipe 540 650 540 1600 250 >>"$LOG" 2>&1; done
  if tap_text_scrolling "AI di HP (offline)"; then
    if tap_text_scrolling "URL HTTPS langsung ke GGUF" field; then
      # Clear both sides of the cursor in a multiline field, independent of
      # tap location/selection/IME. No clipboard helper or debug backdoor.
      $ADB shell input keyevent 123 $(printf '67 %.0s' $(seq 1 240)) $(printf '112 %.0s' $(seq 1 240)) >>"$LOG" 2>&1
      $ADB shell input text "$MODEL_URL" >>"$LOG" 2>&1
      $ADB shell input keyevent KEYCODE_BACK >>"$LOG" 2>&1
      sleep 1
      if tap_text_scrolling "SHA-256 penerbit (opsional)" field; then
        $ADB shell input text "$EXPECTED_SHA" >>"$LOG" 2>&1
        $ADB shell input keyevent KEYCODE_BACK >>"$LOG" 2>&1
        sleep 1
        shot 16a-real-download-settings
        if dump_ui && grep -q "$EXPECTED_SHA" /tmp/ui.xml && tap_text_scrolling "Download & impor GGUF" exact; then
          $ADB shell input swipe 540 1400 540 950 400 >>"$LOG" 2>&1
          DOWNLOADED=0
          for _ in $(seq 1 120); do
            sleep 2
            if dump_ui && grep -q "SHA-256 salinan: $EXPECTED_SHA" /tmp/ui.xml; then DOWNLOADED=1; break; fi
          done
          shot 16b-real-download-complete
          if [ "$DOWNLOADED" = 1 ]; then
            log "event=production-https-model-downloaded-and-hash-verified"
            if tap_text_scrolling "Uji model" exact; then
              $ADB shell input swipe 540 1300 540 950 400 >>"$LOG" 2>&1
              REAL_MODEL_OK=0
              for _ in $(seq 1 90); do
                sleep 2
                if dump_ui; then
                  if grep -q "Terhubung! Model menjawab:" /tmp/ui.xml; then REAL_MODEL_OK=1; break; fi
                  if grep -q "Gagal ·" /tmp/ui.xml; then break; fi
                fi
              done
              shot 16c-real-model-inference
              if [ "$REAL_MODEL_OK" = 1 ]; then log "event=real-gguf-inference-ok"; else FAILED=1; fi
            else FAILED=1; fi
          else FAILED=1; fi
        else FAILED=1; fi
      else FAILED=1; fi
    else FAILED=1; fi
  else FAILED=1; fi
else FAILED=1; fi
$ADB logcat -d -b crash > artifacts/real-model-crash.log 2>/dev/null
$ADB shell dumpsys activity activities > artifacts/real-model-activity.log 2>/dev/null

if [ "$FAILED" -ne 0 ]; then
  shot 16z-final-failure
  dump_ui && cp /tmp/ui.xml artifacts/final-failure.xml
fi
log "ui_issues=$UI_ISSUES"
log "failed=$FAILED"

# Release publication is strict: no hidden allowance for a few UI regressions.
if [ "$UI_ISSUES" -ne 0 ]; then
  log "event=too-many-ui-issues"
  exit 1
fi
[ "$FAILED" -eq 0 ] || exit 1
exit 0
