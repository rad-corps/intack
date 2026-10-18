#!/usr/bin/env node
// One agent at a time in this repo. The lock lives in .git/agent.lock (never committed):
//   {session, kind: "dispatcher" | "interactive", what, pid?, at}
// A dispatcher lock lasts while its claude process runs; an interactive one until the session has gone
// IDLE_MS without editing (or the session ends).
//
//   node scripts/repo-lock.js hook               PreToolUse hook (stdin: the tool call); blocks edits while
//                                                another agent holds the repo, otherwise takes or refreshes it
//   node scripts/repo-lock.js release-session    SessionEnd hook (stdin): lets go if this session holds it
//   node scripts/repo-lock.js acquire <session> <what>   for the dispatcher; exit 1 if someone else holds it
//   node scripts/repo-lock.js setpid <session> <pid>
//   node scripts/repo-lock.js release <session>
//   node scripts/repo-lock.js status
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const LOCK = path.join(ROOT, '.git', 'agent.lock');
const IDLE_MS = 30 * 60_000;
// A dispatcher lock whose process hasn't been recorded yet (just started).
const STARTING_MS = 5 * 60_000;

function read() {
  try { return JSON.parse(fs.readFileSync(LOCK, 'utf8')); } catch { return null; }
}

function alive(pid) {
  try { process.kill(pid, 0); return true; } catch (e) { return e.code === 'EPERM'; }
}

/** Whether [lock] still holds the repo. */
function live(lock) {
  if (!lock) return false;
  if (lock.kind === 'dispatcher') return lock.pid ? alive(lock.pid) : Date.now() - lock.at < STARTING_MS;
  return Date.now() - lock.at < IDLE_MS;
}

/** Takes the lock for [session] unless someone else holds it; returns the holder if not. */
function take(session, kind, what) {
  const current = read();
  if (current && current.session === session) {
    write({ ...current, at: Date.now() });
    return null;
  }
  if (live(current)) return current;
  if (current) fs.rmSync(LOCK, { force: true });
  try {
    fs.writeFileSync(LOCK, JSON.stringify({ session, kind, what, at: Date.now() }), { flag: 'wx' });
    return null;
  } catch {
    return read(); // Someone took it in between.
  }
}

function write(lock) { fs.writeFileSync(LOCK, JSON.stringify(lock)); }

function describe(lock) {
  const since = new Date(lock.at).toLocaleTimeString();
  return lock.kind === 'dispatcher'
    ? `the feedback dispatcher's session for "${lock.what}" (last active ${since})`
    : `another interactive Claude session (last edit at ${since})`;
}

// Tool calls that change the repo. Reads, searches and plain shell look-ups don't need the lock.
const WRITE_TOOLS = new Set(['Edit', 'Write', 'NotebookEdit', 'MultiEdit']);
const WRITE_SHELL = new RegExp([
  String.raw`\bgit\s+(add|commit|push|pull|stash|reset|checkout|switch|merge|rebase|cherry-pick|revert|tag|rm|mv|restore|apply|am|clean)\b`,
  String.raw`\bgradlew`,
  String.raw`release\.ps1`,
  String.raw`\bsed\s+(-[a-zA-Z]*\s+)*-[a-zA-Z]*i`,
  String.raw`\bperl\s+(-[a-zA-Z0-9]*\s+)*-[a-zA-Z0-9]*i`,
].join('|'));

function changesRepo(input) {
  const tool = input.tool_name || '';
  const args = input.tool_input || {};
  if (WRITE_TOOLS.has(tool)) {
    const file = args.file_path || args.notebook_path || '';
    return !file || path.resolve(file).toLowerCase().startsWith(ROOT.toLowerCase());
  }
  if (tool === 'Bash' || tool === 'PowerShell') return WRITE_SHELL.test(args.command || '');
  return false;
}

function stdin() {
  try { return JSON.parse(fs.readFileSync(0, 'utf8') || '{}'); } catch { return {}; }
}

const [cmd, ...args] = process.argv.slice(2);
switch (cmd) {
  case 'hook': {
    const input = stdin();
    if (!input.session_id || !changesRepo(input)) break;
    const holder = take(input.session_id, 'interactive', 'interactive session');
    if (holder) {
      process.stderr.write(
        `The repo is in use by ${describe(holder)}. Only one agent may change it at a time, so this ` +
        `wasn't run. Wait for it to finish (check with: node scripts/repo-lock.js status), or ask Adam.\n`,
      );
      process.exit(2);
    }
    break;
  }
  case 'release-session': {
    const input = stdin();
    const current = read();
    if (current && current.session === input.session_id && current.kind === 'interactive') fs.rmSync(LOCK, { force: true });
    break;
  }
  case 'acquire': {
    const holder = take(args[0], 'dispatcher', args[1] || '');
    if (holder) { console.log(`held by ${describe(holder)}`); process.exit(1); }
    break;
  }
  case 'setpid': {
    const current = read();
    if (current && current.session === args[0]) write({ ...current, pid: Number(args[1]), at: Date.now() });
    break;
  }
  case 'release': {
    const current = read();
    if (current && current.session === args[0]) fs.rmSync(LOCK, { force: true });
    break;
  }
  case 'status': {
    const current = read();
    console.log(live(current) ? `held by ${describe(current)} [session ${current.session}]` : 'free');
    break;
  }
  default:
    console.error('usage: repo-lock.js hook | release-session | acquire <session> <what> | setpid <session> <pid> | release <session> | status');
    process.exit(1);
}
