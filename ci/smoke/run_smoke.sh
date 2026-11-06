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

# The emulator's DNS can lag a few seconds behind boot; the app fetches
# remote data at startup, so wait until the API host resolves before
# launching (first v1.1.1 run failed its data assertion on exactly this).
for i in $(seq 1 45); do
  if adb shell ping -c 1 -W 2 voice.tpgofighting.top >/dev/null 2>&1; then
    note "dns ok after ${i} tries"
    break
  fi
  sleep 2
done

note "installing $APK"
adb install -r "$APK" || fail "adb install failed"

adb logcat -c || true
note "launching $PKG"
adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 8
shot 01-launch; dump 01-launch

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

tap_tab "Obsidian" 04-memory; sleep 3
shot 05-obsidian; dump 05-obsidian
anchor 05-obsidian.xml "Obsidian Vault" "obsidian screen title"
# Data-truth assertion: against the live server the status card must be
# loaded with real values. If the client field mapping breaks, the card
# falls back to the honest unknown state and this gate fails.
if grep -q "状态未知" "$OUT/05-obsidian.xml"; then
  fail "obsidian status not loaded (field mapping broken or server unreachable)"
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
