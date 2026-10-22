#!/usr/bin/env node
// Reads and changes Intack lists straight through the Nostr relays, as a phone would, with no
// emulator. Speaks the app's relay protocol (SyncManager, ListCrypto, Nostr.kt, Merge.kt) and keeps
// its own copy of each space in INTACK_HOME (default %LOCALAPPDATA%\intack-relay). Commands and their
// output match scripts/dev.sh (DevBridge), so the two can stand in for each other:
//   node scripts/relay.js lists | show <list> | add <list> <text> | tick|untick <list> <item>
//   node scripts/relay.js note <list> <item> <text> | messages <group> [count] | say <group> <text>
//   node scripts/relay.js image <list> <item> <out.jpg>     (saves an item's photo)
//   node scripts/relay.js import <identity.xml> <list.json>...  (copy a phone's spaces to start from)
// Lists and items are given by id (an item's first few characters will do) or by name. Every command
// first fetches what's new for the spaces it touches. Relays keep events for 14 days, so a space is
// started from a phone's copy (import), not from the relays alone. Needs @noble/curves (npm install
// in INTACK_HOME, or NODE_PATH pointing at a node_modules that has it).
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const zlib = require('zlib');

const HOME = process.env.INTACK_HOME || path.join(process.env.LOCALAPPDATA || os.homedir(), 'intack-relay');
const { schnorr } = require(require.resolve('@noble/curves/secp256k1', { paths: [HOME, __dirname] }));

const RELAYS = ['wss://relay.intack.app', 'wss://relay.damus.io', 'wss://nos.lol', 'wss://relay.primal.net', 'wss://nostr.mom', 'wss://relay.snort.social'];
const KIND = 4333, LIVE_KIND = 24333, EXPIRY_DAYS = 14, HISTORY_LIMIT = 32;
const WAKE_URL = 'https://i2jb2k75z5ypgyn4c55davrzbi0qahvj.lambda-url.ap-southeast-2.on.aws/';
const FETCH_TIMEOUT_MS = 8000;

const SPACES = path.join(HOME, 'spaces');
const IDENTITY = path.join(HOME, 'identity.json');
fs.mkdirSync(SPACES, { recursive: true });

// --- Store: one JSON file per space, as the app keeps it, plus "since" (newest stored event seen) ---
const readJson = (f, fallback) => (fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : fallback);
const spaceFile = (id) => path.join(SPACES, `${id}.json`);
const allSpaces = () => fs.readdirSync(SPACES).filter((f) => f.endsWith('.json')).map((f) => readJson(path.join(SPACES, f)));
function save(space) {
  const tmp = spaceFile(space.id) + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(space));
  fs.renameSync(tmp, spaceFile(space.id));
}
const identity = () => readJson(IDENTITY, null) || fail(`No identity yet: run "import" first (${IDENTITY}).`);

function fail(message) { throw new Error(message); }

// --- Keys (ListKeys, NostrIdentity) ---
const sha256 = (s) => crypto.createHash('sha256').update(s).digest();
const keys = (secret) => ({
  aes: sha256('twodo/enc/' + secret),
  priv: sha256('twodo/nostr/' + secret),
  get pub() { return Buffer.from(schnorr.getPublicKey(this.priv)).toString('hex'); },
  wake: 'w' + sha256('twodo/wake/' + secret).toString('hex').slice(0, 32),
});

function decrypt(aes, content) {
  try {
    const b = Buffer.from(content, 'base64');
    const d = crypto.createDecipheriv('aes-256-gcm', aes, b.subarray(0, 12));
    d.setAuthTag(b.subarray(b.length - 16));
    const zipped = Buffer.concat([d.update(b.subarray(12, b.length - 16)), d.final()]);
    return JSON.parse(zlib.gunzipSync(zipped).toString('utf8'));
  } catch { return null; }
}

function encrypt(aes, message) {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', aes, iv);
  const body = Buffer.concat([c.update(zlib.gzipSync(Buffer.from(JSON.stringify(message)))), c.final()]);
  return Buffer.concat([iv, body, c.getAuthTag()]).toString('base64');
}

function sign(k, kind, tags, content) {
  const created_at = Math.floor(Date.now() / 1000);
  const pubkey = k.pub;
  const id = sha256(JSON.stringify([0, pubkey, created_at, kind, tags, content])).toString('hex');
  return { id, pubkey, created_at, kind, tags, content, sig: Buffer.from(schnorr.sign(id, k.priv)).toString('hex') };
}

// --- Merging (Merge.kt). Items stay as the JSON they came in, so fields this script doesn't know survive. ---
const cmpV = (a, b) => (!a && !b ? 0 : !a ? -1 : !b ? 1 : a.ts !== b.ts ? a.ts - b.ts : a.by < b.by ? -1 : a.by > b.by ? 1 : 0);
const sameV = (a, b) => cmpV(a, b) === 0;
const hasV = (list, v) => (list || []).some((x) => sameV(x, v));
const later = (l, r, field) => (cmpV(r[field], l[field]) > 0 ? r : l);

function merge(space, remote) {
  space.items ||= {};
  space.superseded ||= {};
  const accepted = [];
  for (const r of remote) {
    const l = space.items[r.id];
    if (!l) { space.items[r.id] = r; accepted.push(r); continue; }
    const lost = space.superseded[r.id] || [];
    let m;
    if (sameV(r.version, l.version) || hasV(l.history, r.version) || hasV(lost, r.version)) m = l;
    else if (hasV(r.history, l.version)) m = r;
    else {
      const remoteWins = cmpV(r.version, l.version) > 0;
      space.superseded[r.id] = [...lost, (remoteWins ? l : r).version];
      m = remoteWins ? r : l;
    }
    m = { ...m };
    const move = cmpV(r.posVersion, l.posVersion) > 0 || (sameV(r.posVersion, l.posVersion) && r.section != null && l.section == null) ? r : l;
    Object.assign(m, { pos: move.pos, posVersion: move.posVersion, section: move.section });
    const note = later(l, r, 'noteVersion');
    Object.assign(m, { note: note.note, noteVersion: note.noteVersion });
    const image = later(l, r, 'imageVersion');
    Object.assign(m, { image: image.image, imageParts: image.imageParts, imageVersion: image.imageVersion });
    const color = later(l, r, 'colorVersion');
    Object.assign(m, { color: color.color, colorVersion: color.colorVersion });
    for (const k of Object.keys(m)) if (m[k] === undefined) delete m[k];
    if (JSON.stringify(m) !== JSON.stringify(l)) { space.items[r.id] = m; accepted.push(m); }
  }
  return accepted;
}

// --- Relays ---
let sockets = null;
function connectAll() {
  if (sockets) return sockets;
  sockets = RELAYS.map((url) => new Promise((resolve) => {
    const ws = new WebSocket(url);
    const timer = setTimeout(() => { try { ws.close(); } catch {} resolve(null); }, 5000);
    ws.onopen = () => { clearTimeout(timer); resolve(ws); };
    ws.onerror = () => { clearTimeout(timer); resolve(null); };
  }));
  return sockets;
}
async function openSockets() { return (await Promise.all(connectAll())).filter(Boolean); }
function closeAll() { if (sockets) sockets.forEach((p) => p.then((ws) => ws && ws.close())); }

/** Fetches what's new for [spaces] from every relay and merges it in; saves the spaces. */
async function fetchNew(spaces) {
  const open = await openSockets();
  if (!open.length) fail('No relay reachable.');
  const names = readJson(path.join(HOME, 'names.json'), {});
  const bySub = new Map();
  spaces.forEach((s, i) => bySub.set(`intack-${i}`, { space: s, k: keys(s.secret) }));
  await Promise.all(open.map((ws) => new Promise((resolve) => {
    const waiting = new Set(bySub.keys());
    const done = () => { ws.removeEventListener('message', onMessage); resolve(); };
    const timer = setTimeout(done, FETCH_TIMEOUT_MS);
    const onMessage = (e) => {
      let m; try { m = JSON.parse(e.data); } catch { return; }
      const sub = bySub.get(m[1]);
      if (!sub) return;
      if (m[0] === 'EOSE') {
        ws.send(JSON.stringify(['CLOSE', m[1]]));
        waiting.delete(m[1]);
        if (!waiting.size) { clearTimeout(timer); done(); }
        return;
      }
      if (m[0] !== 'EVENT') return;
      const ev = m[2];
      if (ev.pubkey !== sub.k.pub || !schnorr.verify(ev.sig, ev.id, ev.pubkey)) return;
      const msg = decrypt(sub.k.aes, ev.content);
      if (!msg) return;
      if (msg.type === 'items') {
        merge(sub.space, msg.items || []);
        sub.space.audit ||= {};
        for (const a of msg.audit || []) sub.space.audit[a.id] ||= a;
        if (ev.kind === KIND) sub.space.relaySince = Math.max(sub.space.relaySince || 0, ev.created_at);
      } else if (msg.type === 'hello' && msg.deviceName) {
        const seen = names[msg.deviceId];
        if (!seen || seen.at <= ev.created_at) names[msg.deviceId] = { name: msg.deviceName, at: ev.created_at };
      }
    };
    ws.addEventListener('message', onMessage);
    for (const [id, { space, k }] of bySub) {
      const filter = { kinds: [KIND, LIVE_KIND], authors: [k.pub] };
      if (space.relaySince) filter.since = space.relaySince - 60;
      ws.send(JSON.stringify(['REQ', id, filter]));
    }
  })));
  spaces.forEach(save);
  fs.writeFileSync(path.join(HOME, 'names.json'), JSON.stringify(names));
}

/** Sends [items] (and [audit]) to the space's members, and wakes their phones. */
async function publish(space, items, audit, urgent) {
  const k = keys(space.secret);
  const expiry = Math.floor(Date.now() / 1000) + EXPIRY_DAYS * 86_400;
  const ev = sign(k, KIND, [['expiration', String(expiry)]], encrypt(k.aes, { type: 'items', items, audit }));
  const open = await openSockets();
  const accepted = await Promise.all(open.map((ws) => new Promise((resolve) => {
    const timer = setTimeout(() => resolve(false), 5000);
    ws.addEventListener('message', (e) => {
      let m; try { m = JSON.parse(e.data); } catch { return; }
      if (m[0] === 'OK' && m[1] === ev.id) { clearTimeout(timer); resolve(m[2] === true); }
    });
    ws.send(JSON.stringify(['EVENT', ev]));
  })));
  if (!accepted.some(Boolean)) fail('No relay accepted the change.');
  merge(space, items);
  space.audit ||= {};
  for (const a of audit) space.audit[a.id] = a;
  save(space);
  try {
    await fetch(WAKE_URL, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ topic: k.wake, urgent }) });
  } catch {}
}

// --- The app's rules for edits (Merge.kt edited/withNote, Changes.kt) ---
function edited(item, change) {
  const me = identity();
  const ts = Math.max(Date.now(), item.version.ts + 1);
  return { ...item, ...change, version: { ts, by: me.deviceId }, editor: me.deviceName, history: [item.version, ...(item.history || [])].slice(0, HISTORY_LIMIT) };
}
function notePreview(note) {
  const first = note.split('\n')[0].trim();
  return first.length > 60 || first.length < note.trim().length ? first.slice(0, 60).trimEnd() + '…' : first;
}
const auditFor = (item, description, v = item.version, kind = '') => ({
  id: `${item.id}/${kind}${v.ts}/${v.by}`, itemId: item.id, ts: v.ts, by: v.by, byName: identity().deviceName, description,
});

// --- Finding things ---
function findSpace(key, kind) {
  const spaces = allSpaces().filter((s) => !kind || s.kind === kind);
  const byId = spaces.find((s) => s.id === key);
  if (byId) return byId;
  const named = spaces.filter((s) => (s.name || '').toLowerCase() === key.toLowerCase());
  if (named.length > 1) fail(`more than one list called ${key}; use its id`);
  return named[0] || fail(`no ${kind || 'list'} ${key}`);
}
const position = (i) => i.pos ?? i.createdAt;
const byPosition = (a, b) => position(a) - position(b) || (a.id < b.id ? -1 : 1);
function visibleItems(space) {
  const items = Object.values(space.items || {}).filter((i) => !i.deleted);
  const headings = items.filter((i) => i.heading).sort(byPosition);
  const ids = new Set(headings.map((h) => h.id));
  const inSection = (s) => items.filter((i) => !i.heading && (ids.has(i.section) ? i.section : null) === s).sort(byPosition);
  return [...inSection(null), ...headings.flatMap((h) => [h, ...inSection(h.id)])];
}
function findItem(space, key) {
  const items = visibleItems(space);
  let found = items.filter((i) => i.id.startsWith(key));
  if (!found.length) found = items.filter((i) => i.text.toLowerCase() === key.toLowerCase());
  if (found.length > 1) fail(`more than one item matches ${key}`);
  return found[0] || fail(`no item ${key} in ${space.name}`);
}

/** The group's spaces this store doesn't have yet (e.g. its messages, started since the import). */
function adoptGroupSpaces(group) {
  for (const ref of Object.values(group.items || {})) {
    if (ref.deleted || !ref.spaceId || !ref.spaceSecret || fs.existsSync(spaceFile(ref.spaceId))) continue;
    save({ id: ref.spaceId, name: ref.text, secret: ref.spaceSecret, kind: ref.chat ? 'chat' : ref.spaceKind || 'list', groupId: group.id, items: {}, audit: {} });
  }
}
// Oldest first: new messages go to the oldest, should there be two (Groups.kt chatRefs).
const chatsOf = (group) => Object.values(group.items || {}).filter((r) => !r.deleted && r.chat && r.spaceId)
  .sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : 1))
  .map((r) => readJson(spaceFile(r.spaceId), null)).filter(Boolean);

// --- Commands ---
async function run(cmd, args) {
  switch (cmd) {
    case 'import': {
      const [idFile, ...files] = args;
      const xml = fs.readFileSync(idFile, 'utf8');
      const get = (n) => (xml.match(new RegExp(`name="${n}">([^<]*)<`)) || [])[1];
      const unescape = (s) => s.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
      fs.writeFileSync(IDENTITY, JSON.stringify({ deviceId: get('deviceId'), deviceName: unescape(get('deviceName') || '') }));
      const known = JSON.parse(unescape(get('knownNames') || '{}'));
      fs.writeFileSync(path.join(HOME, 'names.json'), JSON.stringify(Object.fromEntries(Object.entries(known).map(([id, name]) => [id, { name, at: 0 }]))));
      for (const f of files) save(readJson(f));
      return `imported ${files.length} spaces as ${identity().deviceName}`;
    }
    case 'lists':
      return allSpaces().sort((a, b) => a.name.localeCompare(b.name))
        .map((l) => [l.id, l.kind || 'list', l.name, l.groupId && `group=${l.groupId}`, l.archived && 'archived'].filter(Boolean).join('  ')).join('\n');
    case 'show': {
      const space = findSpace(args[0]);
      await fetchNew([space]);
      return [`${space.name} (${space.id})`, ...visibleItems(space).map((i) => i.heading ? `## ${i.text}  ${i.id}`
        : `[${i.checked ? 'x' : ' '}] ${i.text}${i.image ? ' (has a photo)' : ''}  ${i.id}  by ${i.editor}` + (i.note ? i.note.split('\n').map((l) => `\n      ${l}`).join('') : ''))].join('\n');
    }
    case 'image': {
      const space = findSpace(args[0]);
      await fetchNew([space]);
      const item = findItem(space, args[1]);
      if (!item.image) fail(`${item.text} has no photo`);
      const parts = [];
      for (let n = 0; n < item.imageParts; n++) {
        const part = space.items[`img:${item.image}:${n}`];
        if (!part || !part.text) fail(`only part of the photo has arrived (piece ${n} of ${item.imageParts} missing)`);
        parts.push(part.text);
      }
      fs.writeFileSync(args[2], Buffer.from(parts.join(''), 'base64'));
      return `saved ${args[2]}`;
    }
    case 'add': {
      const space = findSpace(args[0]);
      await fetchNew([space]);
      const me = identity(), now = Date.now();
      const item = { id: crypto.randomUUID(), text: args[1].trim(), createdAt: now, version: { ts: now, by: me.deviceId }, editor: me.deviceName };
      await publish(space, [item], [auditFor(item, `added ${item.text}`)], false);
      return `added to ${space.name}`;
    }
    case 'tick': case 'untick': {
      const space = findSpace(args[0]);
      await fetchNew([space]);
      const before = findItem(space, args[1]);
      const item = edited(before, { checked: cmd === 'tick' });
      await publish(space, [item], [auditFor(item, `${cmd}ed ${item.text}`)], false);
      return `${cmd}ed ${item.id}`;
    }
    case 'note': {
      const space = findSpace(args[0]);
      await fetchNew([space]);
      const before = findItem(space, args[1]);
      const note = args[2].trim() || null;
      if ((before.note || '') === (note || '')) return `noted ${before.id}`;
      const me = identity();
      const v = { ts: Math.max(Date.now(), ((before.noteVersion || {}).ts || 0) + 1), by: me.deviceId };
      const item = { ...before, note, noteVersion: v };
      if (!note) delete item.note;
      const description = !note ? `removed the note from ${item.text}` : !before.note ? `added a note to ${item.text}: ${notePreview(note)}` : `changed the note on ${item.text}: ${notePreview(note)}`;
      await publish(space, [item], [auditFor(item, description, v, 'note/')], false);
      return `noted ${item.id}`;
    }
    case 'messages': {
      const group = findSpace(args[0], 'group');
      await fetchNew([group]);
      adoptGroupSpaces(group);
      const chats = chatsOf(group);
      await fetchNew(chats);
      const me = identity(), names = readJson(path.join(HOME, 'names.json'), {});
      const pad = (n) => String(n).padStart(2, '0');
      const when = (t) => { const d = new Date(t); return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`; };
      const messages = chats.flatMap((c) => Object.values(c.items || {})).filter((m) => !m.deleted && m.text && m.text.trim())
        .sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : 1)).slice(-(+args[1] || 30));
      return [`Messages in ${group.name}`, ...messages.map((m) =>
        `${when(m.createdAt)}  ${m.version.by === me.deviceId ? 'me' : (names[m.version.by] || {}).name || m.editor}: ${m.text}`)].join('\n');
    }
    case 'say': {
      const group = findSpace(args[0], 'group');
      await fetchNew([group]);
      adoptGroupSpaces(group);
      const chat = chatsOf(group)[0] || fail(`${group.name} has no messages yet; send the first one from a phone`);
      const me = identity(), now = Date.now();
      const item = { id: crypto.randomUUID(), text: args[1].trim(), createdAt: now, version: { ts: now, by: me.deviceId }, editor: me.deviceName };
      await publish(chat, [item], [auditFor(item, `added ${item.text}`)], true);
      return `sent to ${group.name}`;
    }
    default:
      fail('usage: relay.js lists | show <list> | add <list> <text> | tick|untick <list> <item> | note <list> <item> <text> | ' +
        'messages <group> [count] | say <group> <text> | image <list> <item> <out.jpg> | import <identity.xml> <list.json>...');
  }
}

run(process.argv[2], process.argv.slice(3))
  .then((out) => { console.log(out); closeAll(); setTimeout(() => process.exit(0), 200); })
  .catch((e) => { console.log(`error: ${e.message}`); closeAll(); setTimeout(() => process.exit(1), 200); });
