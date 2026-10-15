#!/usr/bin/env bash
# Watches a list in the debug app (through scripts/dev.sh) and prints a line for each item or note
# that appears or changes, so an assistant can pick up new work without being asked.
#   bash scripts/watch-dev.sh [list] [seconds]      (default: Dev, every 20 seconds)
LIST=${1:-Dev}; EVERY=${2:-20}
DEV="$(dirname "$0")/dev.sh"
prev=""; down=0
while true; do
  if now=$(bash "$DEV" show "$LIST" 2>&1) && [[ $now != error:* && $now != *"Broadcast completed: result=0"* ]]; then
    [ $down = 1 ] && echo "back: can see $LIST again"; down=0
    if [ -n "$prev" ]; then
      # Lines that are new or changed since last time (an item's line, or a line of its note).
      diff <(printf '%s\n' "$prev") <(printf '%s\n' "$now") | sed -n 's/^> /changed: /p'
      # Items that went away.
      diff <(printf '%s\n' "$prev") <(printf '%s\n' "$now") | grep '^< \[' | sed 's/^< /removed: /'
    fi
    prev=$now
  elif [ $down = 0 ]; then
    echo "can't read $LIST: ${now:0:200}"; down=1
  fi
  sleep "$EVERY"
done
