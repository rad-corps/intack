#!/usr/bin/env bash
# Watches a list in the debug app on the emulator and starts a fresh, unattended Claude Code session
# for each new open item, one at a time. A session that needs more information asks in the group's
# Messages and stops; the next message from someone else in the group resumes it.
#   bash scripts/feedback-dispatcher.sh          (normally started at logon; see the Startup folder)
# State and logs live in %LOCALAPPDATA%\intack-dispatcher: log.txt, runs\, items\ (one file per item).
set -uo pipefail
export MSYS_NO_PATHCONV=1
REPO=/c/dev/twodo
LIST=${LIST:-479db47a-eef0-46c4-a24c-f252d25b8408}   # "App Feedback Notes" in Family
GROUP=${GROUP:-Family}
EVERY=${EVERY:-60}
SDK="$LOCALAPPDATA/Android/Sdk"
export PATH="$SDK/platform-tools:$PATH"
STATE=${STATE:-"$LOCALAPPDATA/intack-dispatcher"}
mkdir -p "$STATE/items" "$STATE/runs"
DEV() { bash "$REPO/scripts/dev.sh" "$@"; }
log() { echo "$(date '+%F %T') $*" | tee -a "$STATE/log.txt"; }

# One dispatcher at a time.
if [ -f "$STATE/pid" ] && kill -0 "$(cat "$STATE/pid")" 2>/dev/null; then echo "already running (pid $(cat "$STATE/pid"))"; exit 0; fi
echo $$ > "$STATE/pid"
trap 'rm -f "$STATE/pid"' EXIT

# The emulator, booted, with the app in front (it only syncs while open).
ensure_app() {
  if ! adb devices | grep -q "^emulator-.*device$"; then
    log "starting the emulator"
    powershell.exe -NoProfile -Command "Start-Process -WindowStyle Hidden '$(cygpath -w "$SDK/emulator/emulator.exe")' -ArgumentList '-avd bigbox -no-window -no-boot-anim -no-snapshot -gpu host -cores 4 -memory 4096 -feature -WiFiPacketStream,-NetsimWebUi,-NetsimCliUi,-BluetoothEmulation'" >/dev/null
    for _ in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break; sleep 5; done
  fi
  adb shell dumpsys activity activities 2>/dev/null | grep -q "ResumedActivity.*app.intack.debug" ||
    adb shell am start -n app.intack.debug/app.intack.MainActivity >/dev/null 2>&1
}

# Runs Claude on an item: a new session, or a resumed one with a reply. Prints the session's status.
run_claude() { # item-id session-id prompt [resume]
  local out="$STATE/runs/$1-$(date +%Y%m%d-%H%M%S).txt"
  local how=(--session-id "$2"); [ "${4:-}" = resume ] && how=(--resume "$2")
  (cd "$REPO" && claude -p "${how[@]}" --permission-mode bypassPermissions "$3" > "$out" 2>&1)
  local status; status=$(grep -o "STATUS: [A-Z]*.*" "$out" | tail -1)
  echo "${status:-STATUS: FAILED no status line (see $out)}"
}

prompt_for() { # id text note
  cat <<EOF
You were started automatically by scripts/feedback-dispatcher.sh, with nobody watching this session.
Adam added this suggestion to a list (id $LIST) in the Intack group "$GROUP":

  Item id: $1
  Item: $2
  Note: ${3:-(none)}

Work it end to end, following the project's usual conventions and your memory: build it, run the unit
tests, check it on the bigbox emulator (already running, debug app open), commit to main, and release
with scripts/release.ps1 (Adam has said to release without asking). Then update the item through
scripts/dev.sh: keep Adam's note text and add a plain-words line such as "Done in v0.9.1: ...", then
tick it:  bash scripts/dev.sh note $LIST $1 "<note>"  and  bash scripts/dev.sh tick $LIST $1
Leave the emulator with the debug app open on its group screen, and the git tree clean.

If you need a decision or more information from Adam, ask in the group's Messages, starting with the
item's name:  bash scripts/dev.sh say "$GROUP" "<item>: <question>"
then stop. You'll be resumed with the reply. If the item is a question rather than something to build,
answer it there and tick the item. Only work on this item.

End your final reply with exactly one line: STATUS: DONE, STATUS: WAITING, or STATUS: FAILED <why>.
EOF
}

# Messages already seen; the first run only takes note of what's there.
seen="$STATE/messages.seen"
[ -f "$seen" ] || DEV messages "$GROUP" | tr -d '\r' > "$seen"

log "watching $LIST in $GROUP"
while true; do
  ensure_app
  # Wait while someone (Adam or another session) has changes in progress.
  if [ -n "$(git -C "$REPO" status --porcelain)" ]; then sleep "$EVERY"; continue; fi

  # Replies: new messages from others go to the item that asked most recently.
  if msgs=$(DEV messages "$GROUP" | tr -d '\r') && [[ $msgs == "Messages in"* ]]; then
    new=$(comm -13 <(sort "$seen") <(printf '%s\n' "$msgs" | sort) | grep -v '^Messages in' | grep -v '  me: ' || true)
    printf '%s\n' "$msgs" > "$seen"
    waiting=$(grep -l "^waiting" "$STATE/items"/* 2>/dev/null | xargs -r ls -t | head -1)
    if [ -n "$new" ] && [ -n "$waiting" ]; then
      id=$(basename "$waiting"); session=$(sed -n 2p "$waiting")
      log "reply for $id; resuming"
      status=$(run_claude "$id" "$session" "Reply in $GROUP's Messages:
$new

Carry on with the item. End with exactly one line: STATUS: DONE, STATUS: WAITING, or STATUS: FAILED <why>." resume)
      log "$id: $status"
      printf '%s\n%s\n' "$(echo "${status#STATUS: }" | cut -d' ' -f1 | tr A-Z a-z)" "$session" > "$waiting"
      continue
    fi
  fi

  # New open items: "[ ] text  <uuid>  by Name", then its note, indented.
  shown=$(DEV show "$LIST" | tr -d '\r') || { sleep "$EVERY"; continue; }
  next=$(printf '%s\n' "$shown" | grep -E '^\[ \] ' | while read -r line; do
    id=$(grep -oE '[0-9a-f]{8}-[0-9a-f-]{27}' <<<"$line" | tail -1)
    [ -n "$id" ] && [ ! -f "$STATE/items/$id" ] && { echo "$id"; break; }
  done)
  if [ -n "$next" ]; then
    text=$(printf '%s\n' "$shown" | grep -F "$next" | sed -E 's/^\[ \] //; s/  [0-9a-f-]{36}  by .*//')
    note=$(printf '%s\n' "$shown" | awk -v id="$next" 'index($0, id) { on = 1; next } on && /^      / { sub(/^      /, ""); print; next } { on = 0 }')
    session=$(powershell.exe -NoProfile -Command "[guid]::NewGuid().ToString()" | tr -d '\r')
    printf 'running\n%s\n' "$session" > "$STATE/items/$next"
    log "new item $next: $text (session $session)"
    status=$(run_claude "$next" "$session" "$(prompt_for "$next" "$text" "$note")")
    log "$next: $status"
    printf '%s\n%s\n' "$(echo "${status#STATUS: }" | cut -d' ' -f1 | tr A-Z a-z)" "$session" > "$STATE/items/$next"
    continue
  fi
  sleep "$EVERY"
done
