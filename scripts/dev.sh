#!/usr/bin/env bash
# Reads and changes lists in the debug app on a connected phone or emulator, through DevBridge
# (debug builds only). Lists and items can be given by id (or an item id's first few characters)
# or by name.
#   bash scripts/dev.sh lists
#   bash scripts/dev.sh show Dev
#   bash scripts/dev.sh add Dev "Fix the thing"          (add-heading Dev "Later" for a heading)
#   bash scripts/dev.sh tick Dev 3f2a                     (untick to undo)
#   bash scripts/dev.sh newlist "Adam and Claude" Dev
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
P=app.twodo.debug

q() { printf "'%s'" "${1//\'/\'\\\'\'}"; } # quotes an argument for the phone's shell
send() { # cmd extras...
  local out
  out=$($ADB shell "am broadcast -n $P/app.twodo.DevBridge -a app.twodo.DEV --es cmd $1 ${*:2}" | tr -d '\r')
  # am prints: Broadcast completed: result=0, data="..."
  out=${out#*data=\"}
  printf '%s\n' "${out%\"}"
}

cmd=${1:-}; shift || true
case $cmd in
  lists) send lists ;;
  show) send show --es list "$(q "$1")" ;;
  add) send add --es list "$(q "$1")" --es text "$(q "$2")" ;;
  add-heading) send add --es list "$(q "$1")" --es text "$(q "$2")" --ez heading true ;;
  tick|untick) send "$cmd" --es list "$(q "$1")" --es item "$(q "$2")" ;;
  newlist) send newlist --es group "$(q "$1")" --es name "$(q "$2")" ;;
  *) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
