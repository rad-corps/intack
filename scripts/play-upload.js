#!/usr/bin/env node
// Publishes an app bundle to a Google Play testing track, through the Play Developer API and the
// play-publisher service account (key file outside the repo; see docs/play/PLAY_STORE.md).
//   node scripts/play-upload.js check                         can we reach the app? (changes nothing)
//   node scripts/play-upload.js upload <bundle.aab> <name> <notes-file> [track]
// The track defaults to "internal". Release notes are plain text, at most 500 characters on Play.
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');

const PACKAGE = 'app.intack';
const KEY = process.env.PLAY_KEY || path.join(os.homedir(), 'secrets', 'intack-ca502-19e751395e1b.json');
const API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}`;
const UPLOAD = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${PACKAGE}`;

const b64url = (data) => Buffer.from(data).toString('base64url');

/** An OAuth access token for the service account. */
async function token() {
  const key = JSON.parse(fs.readFileSync(KEY, 'utf8'));
  const now = Math.floor(Date.now() / 1000);
  const claims = {
    iss: key.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: key.token_uri,
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))}.${b64url(JSON.stringify(claims))}`;
  const signature = crypto.createSign('RSA-SHA256').update(unsigned).sign(key.private_key, 'base64url');
  const res = await fetch(key.token_uri, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${signature}` }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`Signing in as ${key.client_email} failed: ${JSON.stringify(body)}`);
  return body.access_token;
}

async function call(auth, method, url, body, contentType = 'application/json') {
  const res = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${auth}`, ...(body && { 'Content-Type': contentType }) },
    body: body && (contentType === 'application/json' ? JSON.stringify(body) : body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${url.replace(API, '').replace(UPLOAD, '')}: ${res.status} ${text}`);
  return text ? JSON.parse(text) : {};
}

/** Play's release notes: plain text, at most 500 characters. */
function playNotes(markdown) {
  // The changelog wraps its lines; Play would show each break, so only blank lines and bullets stay.
  const text = markdown.replace(/\*\*|__|`/g, '').replace(/\r/g, '').trim().split(/\n\s*\n/)
    .map((p) => p.replace(/\n\s*(?![-*] )/g, ' ').replace(/^[-*] /gm, '• '))
    .join('\n\n');
  return text.length <= 500 ? text : `${text.slice(0, 497).replace(/\s+\S*$/, '')}…`;
}

async function main() {
  const [cmd, ...args] = process.argv.slice(2);
  const auth = await token();
  const edit = await call(auth, 'POST', `${API}/edits`);
  const at = `${API}/edits/${edit.id}`;
  let committed = false;
  try {
    if (cmd === 'check') {
      const { tracks } = await call(auth, 'GET', `${at}/tracks`);
      for (const t of tracks) {
        const releases = (t.releases || []).map((r) => `${r.name} (${r.status})`).join(', ');
        console.log(`${t.track}: ${releases || 'no releases'}`);
      }
      return;
    }
    if (cmd !== 'upload' || args.length < 3) {
      throw new Error('usage: play-upload.js check | upload <bundle.aab> <name> <notes-file> [track]');
    }
    const [bundle, name, notesFile, track = 'internal'] = args;
    const { versionCode } = await call(auth, 'POST', `${UPLOAD}/edits/${edit.id}/bundles?uploadType=media`,
      fs.readFileSync(bundle), 'application/octet-stream');
    await call(auth, 'PUT', `${at}/tracks/${track}`, {
      track,
      releases: [{
        name,
        versionCodes: [String(versionCode)],
        status: 'completed',
        releaseNotes: [{ language: 'en-GB', text: playNotes(fs.readFileSync(notesFile, 'utf8')) }],
      }],
    });
    await call(auth, 'POST', `${at}:commit`);
    committed = true;
    console.log(`Published ${name} (versionCode ${versionCode}) to the ${track} track on Google Play.`);
  } finally {
    if (!committed) await call(auth, 'DELETE', at).catch(() => {});
  }
}

main().catch((e) => { console.error(e.message); process.exit(1); });
