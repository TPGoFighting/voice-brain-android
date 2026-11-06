#!/usr/bin/env bash
# Voice Brain Android - emulator smoke test.
# Runs on the GitHub Actions runner inside the `script:` block of
# reactivecircus/android-emulator-runner. Installs the release APK, walks
# the four main tabs, screenshots every screen, and fails the job on
# process death, a fatal crash, or a missing screen anchor.
set -u

PKG="top.tpgofighting.voicebrain"
APK="${APK_PATH:-app-release.apk}"
OUT="smoke-out"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
FAILURES=""

note() { echo "[smoke] $*"; }
fail() { FAILURES="${FAILURES}\n- $1"; note "FAIL: $1"; }

shot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || note "screencap $1 failed"; }
dump() {
  adb shell uiautomator dump /sdcard/dump.xml >/dev/null 2>&1
  adb pull /sdcard/dump.xml "$OUT/$1.xml" >/dev/null 2>&1 || note "dump $1 failed"
}
tap_tab() { # <tab label> <dump name the label is read from>
  local coords
  if coords="$(python3 "$HERE/tap_text.py" "$OUT/$2.xml" "$1")"; then
    note "tap '$1' at $coords"
    adb shell input tap $coords
  else
    fail "tab '$1' not found in UI dump $2"
  fi
}
anchor() { # <dump file> <text> <label>
  if grep -q "$2" "$OUT/$1" 2>/dev/null; then
    note "anchor ok: $3"
  else
    fail "anchor missing: $3 ('$2' not in $1)"
  fi
}

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
note "device booted"

# No network pre-check: ICMP ping is unreliable through the emulator NAT.
# Whether the API was reachable is judged afterwards from the app's own
# logcat lines (see the obsidian data assertion below).

note "installing $APK"
adb install -r "$APK" || fail "adb install failed"

adb logcat -c || true
note "launching $PKG"
adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 8
shot 01-launch; dump 01-launch

# Permissions are on-demand only (v1.1.2): no system permission dialog
# may appear at launch. Before this fix the mic dialog popped up on the
# very first frame, ahead of any explanation.
if grep -q "permissioncontroller" "$OUT/01-launch.xml"; then
  fail "system permission dialog shown at launch (permissions must be on-demand)"
else
  note "no permission dialog at launch"
fi

# Grant runtime permissions so every main screen is fully reachable.
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
sleep 2
shot 02-live; dump 02-live
anchor 02-live.xml "开始实时录音" "live screen record button"

tap_tab "纪要决策" 02-live; sleep 3
shot 03-minutes; dump 03-minutes
anchor 03-minutes.xml "结构化纪要与决策对账" "minutes screen title"

tap_tab "记忆大脑" 03-minutes; sleep 3
shot 04-memory; dump 04-memory
anchor 04-memory.xml "全文检索" "memory screen search subtab"

# Memory empty state (v1.1.2): with no memories the library must explain
# itself and offer a way to record, not sit blank. When the API answered
# with data the empty state legitimately does not appear.
if grep -q "去录一段" "$OUT/04-memory.xml"; then
  note "memory empty state shown with record call-to-action"
elif adb logcat -d 2>/dev/null | grep -a "fetchMemories failed" | grep -qaE "Unable to resolve host|Connection reset|Failed to connect|timeout|Timeout"; then
  fail "memories unfetchable from this network but no empty state was shown"
else
  note "memories present (or fetch still pending); empty-state check not applicable"
fi

tap_tab "Obsidian" 04-memory; sleep 3
shot 05-obsidian; dump 05-obsidian
anchor 05-obsidian.xml "Obsidian Vault" "obsidian screen title"
# Data-truth assertion: against the live server the status card must be
# loaded with real values. Give the tab's auto-retry a few chances first.
for i in 1 2 3 4; do
  grep -q "状态未知" "$OUT/05-obsidian.xml" || break
  note "obsidian status pending, waiting for auto-retry ($i)"
  sleep 5
  dump 05-obsidian
done
shot 05-obsidian
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
if grep -q "状态未知" "$OUT/05-obsidian.xml"; then
  # Distinguish "runner network cannot reach the API at all" (the CDN
  # resets connections from some datacenter ranges; the field mapping
  # itself is guarded by JVM unit tests in the build workflow) from
  # "API answered but the client failed to use it", which is a real bug.
  if grep -a "fetchObsidianStatus failed" "$OUT/logcat.txt" | grep -qaE "Unable to resolve host|Connection reset|Failed to connect|timeout|Timeout"; then
    note "WARN: API unreachable from this runner network; data assertion skipped (mapping covered by unit tests)"
  else
    fail "obsidian status not loaded although the API was reachable (field mapping broken?)"
  fi
else
  note "obsidian status loaded with real server data"
fi

# Liveness + crash checks.
PID="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
if [ -n "$PID" ]; then note "process alive: pid $PID"; else fail "app process not running at end"; fi
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
if grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  fail "fatal exception for $PKG in logcat"
else
  note "no fatal exception for $PKG"
fi

if [ -n "$FAILURES" ]; then
  printf "SMOKE FAILURES:%b\n" "$FAILURES"
  exit 1
fi
note "SMOKE PASSED"
