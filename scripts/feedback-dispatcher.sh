#!/usr/bin/env bash
# Watches a list through the relays (scripts/relay.js, no emulator) and starts a fresh, unattended Claude
# Code session for each new open item, one at a time. A session that needs more information asks in the
# group's Messages and stops; the next message from someone else in the group resumes it.
#   bash scripts/feedback-dispatcher.sh          (normally started at logon; see the Startup folder)
# State and logs live in %LOCALAPPDATA%\intack-dispatcher: log.txt, runs\, items\ (one file per item).
# The relay client's own copy of the group (as "Adam's autonomous assistant") is in %LOCALAPPDATA%\intack-relay.
# The emulator runs only while a session checks something on screen (scripts/emu.sh), and Gradle's
# daemons are stopped after each session, so nothing heavy runs while it's only watching.
set -uo pipefail
export MSYS_NO_PATHCONV=1
REPO=/c/dev/twodo
LIST=${LIST:-479db47a-eef0-46c4-a24c-f252d25b8408}   # "App Feedback Notes" in Family
GROUP=${GROUP:-Family}
EVERY=${EVERY:-60}
export PATH="$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"
# bigbox, for sessions using scripts/dev.sh; other emulators may be running too, and plain adb refuses to pick.
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}
# A Unix-style path: xargs and friends treat the backslashes in $LOCALAPPDATA as escapes.
STATE=$(cygpath -u "${STATE:-"$LOCALAPPDATA/intack-dispatcher"}")
mkdir -p "$STATE/items" "$STATE/runs"
LOCK=$(cygpath -w "$REPO/scripts/repo-lock.js")   # node needs a Windows path
RELAY=$(cygpath -w "$REPO/scripts/relay.js")
DEV() { node "$RELAY" "$@"; }
log() { echo "$(date '+%F %T') $*" | tee -a "$STATE/log.txt"; }

# One dispatcher at a time.
if [ -f "$STATE/pid" ] && kill -0 "$(cat "$STATE/pid")" 2>/dev/null; then echo "already running (pid $(cat "$STATE/pid"))"; exit 0; fi
echo $$ > "$STATE/pid"
trap 'rm -f "$STATE/pid"' EXIT

# After a session: Gradle's daemons and the emulator hold ~8 GB and keep cores busy even when idle.
tidy_up() {
  (cd "$REPO" && ./gradlew --stop -q) >/dev/null 2>&1
  powershell.exe -NoProfile -Command 'Get-CimInstance Win32_Process | Where-Object { $_.Name -eq "java.exe" -and $_.CommandLine -like "*KotlinCompileDaemon*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }' >/dev/null 2>&1
  bash "$REPO/scripts/emu.sh" stop >/dev/null
}

# Runs Claude on an item: a new session, or a resumed one with a reply. Prints the session's status.
run_claude() { # item-id session-id prompt [resume] [item-text]
  local out="$STATE/runs/$1-$(date +%Y%m%d-%H%M%S).txt"
  local how=(--session-id "$2"); [ "${4:-}" = resume ] && how=(--resume "$2")
  # The session has the repo to itself until it ends (scripts/repo-lock.js; other sessions' edits are blocked).
  node "$LOCK" acquire "$2" "${5:-$1}" >/dev/null || { echo "STATUS: BUSY"; return; }
  (cd "$REPO" && claude -p "${how[@]}" --permission-mode bypassPermissions "$3" > "$out" 2>&1) &
  local job=$!
  node "$LOCK" setpid "$2" "$(cat /proc/$job/winpid 2>/dev/null || echo 0)"
  wait $job
  node "$LOCK" release "$2"
  tidy_up
  local status; status=$(grep -o "STATUS: [A-Z]*.*" "$out" | tail -1)
  echo "${status:-STATUS: FAILED no status line (see $out)}"
}

# An item's text and note as the list shows them ("[ ] text  <uuid>  by Name", then the note, indented).
item_text() { printf '%s\n' "$2" | grep -F "$1" | sed -E 's/^\[.\] //; s/  [0-9a-f-]{36}  by .*//'; }
item_note() { printf '%s\n' "$2" | awk -v id="$1" 'index($0, id) { on = 1; next } on && /^      / { sub(/^      /, ""); print; next } { on = 0 }'; }

prompt_for() { # id text note who
  cat <<EOF
You were started automatically by scripts/feedback-dispatcher.sh, with nobody watching this session.
${4:-Someone} added this suggestion to a list (id $LIST) in the Intack group "$GROUP". Adam and his wife
are both in the group, and either of them may direct you.

  Item id: $1
  Item: $2
  Note: ${3:-(none)}

Work it end to end, following the project's usual conventions and your memory: build it, run the unit
tests, check it on screen on the bigbox emulator, commit to main, and release with scripts/release.ps1
(Adam has said to release without asking). The emulator isn't running: start it with
bash scripts/emu.sh start (it returns once the debug app is open), and stop it with bash scripts/emu.sh stop
once you've finished with it. If the item says "(has a photo)", look at the photo first:
  node scripts/relay.js image $LIST $1 <file.jpg>    then read that file.
Then update the item through scripts/relay.js, which talks to the relays directly as the group's phones
do: keep the original note text and add a plain-words line such as "Done in v0.9.1: ...", then tick it:
  node scripts/relay.js note $LIST $1 "<note>"  and  node scripts/relay.js tick $LIST $1
Leave the git tree clean.

Whenever you need clarification, a decision, or permission (anything risky, destructive, costly, or
outside this repo), ask in the group's Messages rather than guessing, starting with the item's name:
  node scripts/relay.js say "$GROUP" "<item>: <question>"
then stop. You'll be resumed with the reply. If the item is a question rather than something to build,
answer it there and tick the item. Only work on this item.

The dispatcher has already told the group you're starting. When you finish, post one short message
in plain words saying what changed and where to find it (e.g. the version, and where it is in the app):
  node scripts/relay.js say "$GROUP" "Done: <item>. <what changed>"

End your final reply with exactly one line: STATUS: DONE, STATUS: WAITING, or STATUS: FAILED <why>.
EOF
}

# Messages already seen; the first run only takes note of what's there.
seen="$STATE/messages.seen"
[ -f "$seen" ] || DEV messages "$GROUP" | tr -d '\r' > "$seen"

log "watching $LIST in $GROUP"
while true; do
  # Wait while another agent holds the repo, or anyone has changes in progress.
  if [ "$(node "$LOCK" status)" != free ]; then sleep "$EVERY"; continue; fi
  # Wait while someone (Adam or an interactive session) has changes in progress.
  if [ -n "$(git -C "$REPO" status --porcelain)" ]; then sleep "$EVERY"; continue; fi

  # Replies: new messages from others go to the item that asked most recently.
  if msgs=$(DEV messages "$GROUP" | tr -d '\r') && [[ $msgs == "Messages in"* ]]; then
    new=$(comm -13 <(sort "$seen") <(printf '%s\n' "$msgs" | sort) | grep -v '^Messages in' | grep -v '  me: ' || true)
    printf '%s\n' "$msgs" > "$seen"
    waiting=$(ls -t "$STATE/items"/* 2>/dev/null | while read -r f; do grep -q "^waiting" "$f" && { echo "$f"; break; }; done)
    if [ -n "$new" ] && [ -n "$waiting" ]; then
      id=$(basename "$waiting"); session=$(sed -n 2p "$waiting")
      log "reply for $id; resuming"
      # The item as it is now: people often answer by editing it or adding to its note.
      shown=$(DEV show "$LIST" | tr -d '\r'); note=$(item_note "$id" "$shown")
      status=$(run_claude "$id" "$session" "Reply in $GROUP's Messages:
$new

The item now reads:
  Item: $(item_text "$id" "$shown")
  Note: ${note:-(none)}

Carry on with the item. End with exactly one line: STATUS: DONE, STATUS: WAITING, or STATUS: FAILED <why>." resume)
      log "$id: $status"
      [[ $status == "STATUS: FAILED"* ]] && DEV say "$GROUP" "Couldn't finish that one (${status#STATUS: FAILED }). Adam can see the details on the PC." >/dev/null
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
  # Start an item only once it has stayed the same for a round, so a note still being typed isn't missed.
  if [ -n "$next" ]; then
    text=$(item_text "$next" "$shown"); note=$(item_note "$next" "$shown")
    [ "$next $text $note" != "${settling:-}" ] && { settling="$next $text $note"; sleep "$EVERY"; continue; }
    who=$(printf '%s\n' "$shown" | grep -F "$next" | sed -E 's/.*  by //')
    session=$(powershell.exe -NoProfile -Command "[guid]::NewGuid().ToString()" | tr -d '\r')
    printf 'running\n%s\n' "$session" > "$STATE/items/$next"
    log "new item $next: $text (session $session)"
    DEV say "$GROUP" "Starting on: $text" >/dev/null
    status=$(run_claude "$next" "$session" "$(prompt_for "$next" "$text" "$note" "$who")" new "$text")
    log "$next: $status"
    # Lost a race for the repo: try this item again later.
    [ "$status" = "STATUS: BUSY" ] && { rm -f "$STATE/items/$next"; sleep "$EVERY"; continue; }
    [[ $status == "STATUS: FAILED"* ]] && DEV say "$GROUP" "Couldn't finish: $text (${status#STATUS: FAILED }). Adam can see the details on the PC." >/dev/null
    printf '%s\n%s\n' "$(echo "${status#STATUS: }" | cut -d' ' -f1 | tr A-Z a-z)" "$session" > "$STATE/items/$next"
    continue
  fi
  sleep "$EVERY"
done
