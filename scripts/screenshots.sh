#!/usr/bin/env bash
# Store screenshots with demo data (scripts/demo_data.js). Needs two running emulators with the debug
# build installed: SHOT (the phone in the screenshots, "James") and PEER ("Sarah"), so the groups really
# show as connected. Replaces the debug app's data on both, so never point it at an emulator with real
# groups on it (bigbox): make fresh AVDs for it, e.g. "shot" on -port 5560 and "peer" on -port 5562.
#   SHOT=emulator-5560 PEER=emulator-5562 bash scripts/screenshots.sh [out_dir]   (default docs/play/screenshots)
#   ... bash scripts/screenshots.sh --load-only      (just load the demo data and open the app)
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
SHOT=${SHOT:-emulator-5560}; PEER=${PEER:-emulator-5562}; P=app.intack.debug
LOAD_ONLY=; [ "${1:-}" = --load-only ] && { LOAD_ONLY=1; shift; }
OUT=${1:-docs/play/screenshots}
TMP=$(mktemp -d)
# On Windows (Git Bash), node needs the Windows form of the temp path.
command -v cygpath >/dev/null && TMP=$(cygpath -m "$TMP")
node scripts/demo_data.js "$TMP"

load() { # serial who
  $ADB -s $1 shell am force-stop $P
  $ADB -s $1 shell pm clear $P >/dev/null
  $ADB -s $1 shell pm grant $P android.permission.POST_NOTIFICATIONS
  $ADB -s $1 shell "run-as $P mkdir -p files/lists shared_prefs"
  $ADB -s $1 push "$TMP/$2/identity.xml" /data/local/tmp/identity.xml >/dev/null 2>&1
  $ADB -s $1 shell "run-as $P cp /data/local/tmp/identity.xml shared_prefs/identity.xml"
  for f in "$TMP/$2/lists/"*.json; do
    $ADB -s $1 push "$f" /data/local/tmp/list.json >/dev/null 2>&1
    $ADB -s $1 shell "run-as $P cp /data/local/tmp/list.json files/lists/$(basename "$f")"
  done
}
load $PEER sarah
load $SHOT me
$ADB -s $PEER shell am start -n $P/app.intack.MainActivity >/dev/null
$ADB -s $SHOT shell am start -n $P/app.intack.MainActivity >/dev/null
# Clean status bar: fixed time, full battery and signal, no notifications.
$ADB -s $SHOT shell settings put global sysui_demo_allowed 1
# The clock shows the real time, so it matches the "5 min ago" and History times in the demo data.
for cmd in "enter" "clock -e hhmm $(date +%H%M)" "battery -e level 100 -e plugged false" "network -e wifi show -e level 4 -e fully true" "network -e mobile hide" "notifications -e visible false"; do
  set -- $cmd; c=$1; shift
  $ADB -s $SHOT shell am broadcast -a com.android.systemui.demo -e command $c "$@" >/dev/null
done
[ -n "$LOAD_ONLY" ] && { echo "Demo data loaded"; exit 0; }
# Long enough for the "Sarah joined" notes to come and go.
echo "Waiting for the phones to connect..."; sleep 45

# Taps the first node (or with "last", the last) whose text or content-desc is $1 (a grep pattern);
# fails if there's none. Retries, since uiautomator can't dump while the screen is animating.
tap_text() {
  local pick=head b=
  [ "${2:-}" = last ] && pick=tail
  for _ in 1 2 3 4 5; do
    $ADB -s $SHOT shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    b=$($ADB -s $SHOT shell cat /sdcard/ui.xml | grep -o "<node [^>]*\(text\|content-desc\)=\"$1\"[^>]*>" | $pick -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ' || true)
    [ -n "$b" ] && break; sleep 2
  done
  [ -n "$b" ] || { echo "  no \"$1\" on screen" >&2; exit 1; }
  set -- $b; $ADB -s $SHOT shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 2.5
}
shot() { sleep 1; $ADB -s $SHOT exec-out screencap -p > "$OUT/$1.png"; echo "  $1"; }
back() { $ADB -s $SHOT shell input keyevent 4; sleep 1.5; }

mkdir -p "$OUT"
rm -f "$OUT"/*.png
shot 1-home
tap_text "Parker family" last; shot 2-calendar
tap_text "Lists"; tap_text "Groceries"; shot 3-list
tap_text "More"; tap_text "History"; shot 8-history; back; back
tap_text "Messages"; shot 4-messages
tap_text "Lists"; tap_text "Garden jobs"; shot 5-photo; back
tap_text "Invite someone"; shot 7-invite; back
back; tap_text "Saturday football" last; shot 6-own-look
$ADB -s $SHOT shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
echo "Screenshots in $OUT"
