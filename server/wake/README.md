# Wake server

A tiny AWS Lambda that lets phones sleep instead of staying connected. When something changes in a
space, the phone that made the change asks this server to send a **content-free** push to that
space's topic. Every member's phone is subscribed to the topic, wakes for a few seconds, fetches the
change through the relays as before, decrypts it and decides whether to notify.

What the server sees: a topic (a hash of the space's secret, meaningless without it), the sender's
IP address and the time. It never sees content, names, members or device tokens: Firebase keeps the
topic subscriptions.

## How it fits

```
phone A ──edit──▶ relays (encrypted, as now)
   │
   └─POST /wake {topic}──▶ this Lambda ──▶ FCM topic ──▶ phones B, C wake ──▶ fetch from relays
```

- `urgent: true` (messages) → high priority, delivered at once even in Doze.
- otherwise (list edits) → normal priority; Android may hold it until the next maintenance window.
- Android's rule: a high-priority push the app doesn't turn into a notification counts against the
  app, so only messages are urgent.
- iOS (later) gets a generic alert with `mutable-content`, which its Notification Service Extension
  replaces with the decrypted text; iOS doesn't let silent pushes run code reliably.

## Setup (once)

1. **Firebase project** (free): console.firebase.google.com → add project "intack" → add Android apps
   `app.intack` and `app.intack.debug` → download `google-services.json` (not secret; it goes in `app/`).
   Then Project settings → Service accounts → Generate new private key: that JSON **is** secret, and
   only goes into the Lambda's environment.
2. **Lambda**: `bash server/wake/deploy.sh` (AWS profile `intack`, key at `~/secrets/intack-fcm.json`). It creates
   or updates `intack-wake` (Node.js 22, 128 MB, 10 s, role `intack-wake-role` that can only write
   its logs) with a public Function URL, and puts the key in the function's environment from a file.
3. The Function URL goes into the app build as the gradle property `intack.wakeUrl`.

Live since 2026-10-17 in account `intack` (Sydney, ap-southeast-2):
`https://i2jb2k75z5ypgyn4c55davrzbi0qahvj.lambda-url.ap-southeast-2.on.aws/`

Cost at family scale: within the always-free Lambda tier (1M requests a month); FCM is free.

## Test

```
node --test server/wake
```
