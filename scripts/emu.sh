#!/usr/bin/env bash
# Starts or stops the bigbox emulator (emulator-5554) with the debug app open, for checking a change on
# screen. It costs ~3 GB of RAM and about two cores while it runs, so stop it when you're done.
#   bash scripts/emu.sh start     (boots it if needed and opens the app; returns once it's up)
#   bash scripts/emu.sh stop
#   bash scripts/emu.sh status
set -uo pipefail
export MSYS_NO_PATHCONV=1
SDK="$LOCALAPPDATA/Android/Sdk"
export PATH="$SDK/platform-tools:$PATH"
SERIAL=emulator-5554
ADB="adb -s $SERIAL"
up() { adb devices | grep -q "^$SERIAL.*device$"; }

case ${1:-} in
  start)
    if ! up; then
      # netsim and the other extras off: netsimd crashes on this CPU (see the dev-group-emulator notes).
      powershell.exe -NoProfile -Command "Start-Process -WindowStyle Hidden '$(cygpath -w "$SDK/emulator/emulator.exe")' -ArgumentList '-avd bigbox -no-window -no-boot-anim -no-snapshot -gpu host -cores 4 -memory 4096 -feature -WiFiPacketStream,-NetsimWebUi,-NetsimCliUi,-BluetoothEmulation'" >/dev/null
      for _ in $(seq 1 60); do [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 5; done
    fi
    $ADB shell am start -n app.intack.debug/app.intack.MainActivity >/dev/null 2>&1
    up && echo "bigbox is up ($SERIAL), debug app open" || { echo "bigbox didn't start"; exit 1; } ;;
  stop) up && $ADB emu kill >/dev/null; echo "bigbox stopped" ;;
  status) up && echo running || echo stopped ;;
  *) sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
