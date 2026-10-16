// Intack wake server: an AWS Lambda (Function URL) that turns "something changed in space X" into a
// content-free push through Firebase Cloud Messaging, so phones can sleep instead of staying
// connected. It never sees list content, names or who is in a space: a phone sends only a topic
// derived from the space's secret, and every member's phone subscribes to that topic itself.
//
// POST /wake  {"topic": "w0123…", "urgent": true}
//   topic:  "w" + 32 hex characters (see Wake.kt: sha256 of the space secret, so only members know it)
//   urgent: a message someone should see now (high priority); otherwise a normal-priority nudge
//           that Android may hold until the phone's next maintenance window.
// → 202 sent, 400 bad request, 429 too soon for that topic, 502 FCM refused.
//
// Configuration (Lambda environment): FCM_SERVICE_ACCOUNT = the Firebase service account JSON.
// No dependencies: the Google OAuth token is signed here with node:crypto.

import { createSign } from "node:crypto";

const TOPIC = /^w[0-9a-f]{32}$/;
/** At most one wake per topic this often, per warm instance (best effort; FCM collapses the rest). */
const MIN_GAP_MS = 5_000;
/** A wake older than this isn't worth delivering: the phone catches up when next opened anyway. */
const TTL_S = 3_600;

const lastSent = new Map();
let token = null; // { value, expiresAt }

export const handler = async (event) => {
    if ((event.requestContext?.http?.method ?? "POST") !== "POST") return reply(405, "POST only");
    let body;
    try {
        body = JSON.parse(event.isBase64Encoded ? Buffer.from(event.body ?? "", "base64").toString() : event.body ?? "");
    } catch {
        return reply(400, "not JSON");
    }
    const problem = validate(body);
    if (problem) return reply(400, problem);
    if (!allow(body.topic, Date.now())) return reply(429, "too soon");
    const account = JSON.parse(process.env.FCM_SERVICE_ACCOUNT);
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
        method: "POST",
        headers: { authorization: `Bearer ${await accessToken(account)}`, "content-type": "application/json" },
        body: JSON.stringify({ message: wakeMessage(body.topic, body.urgent === true) }),
    });
    if (!res.ok) {
        console.warn("FCM refused", res.status, await res.text());
        return reply(502, "push service refused");
    }
    return reply(202, "sent");
};

export function validate(body) {
    if (typeof body !== "object" || body === null) return "expected an object";
    if (typeof body.topic !== "string" || !TOPIC.test(body.topic)) return "bad topic";
    if (body.urgent !== undefined && typeof body.urgent !== "boolean") return "urgent must be true or false";
    return null;
}

/** Rate limit per topic; also forgets old entries so a long-lived instance doesn't grow. */
export function allow(topic, now) {
    const last = lastSent.get(topic);
    if (last !== undefined && now - last < MIN_GAP_MS) return false;
    lastSent.set(topic, now);
    if (lastSent.size > 10_000) for (const [t, at] of lastSent) if (now - at > MIN_GAP_MS) lastSent.delete(t);
    return true;
}

/**
 * The push itself. Android gets a data-only message (the app decides whether to notify, after
 * fetching and decrypting). iOS (later) can't run code for a silent push reliably, so it gets a
 * generic alert its Notification Service Extension replaces with the real, decrypted text.
 */
export function wakeMessage(topic, urgent) {
    return {
        topic,
        data: { wake: topic },
        android: { priority: urgent ? "HIGH" : "NORMAL", ttl: `${TTL_S}s`, collapse_key: topic },
        apns: {
            headers: { "apns-priority": urgent ? "10" : "5", "apns-collapse-id": topic, "apns-expiration": String(Math.floor(Date.now() / 1000) + TTL_S) },
            payload: { aps: urgent ? { alert: { title: "Intack", body: "New activity" }, "mutable-content": 1 } : { "content-available": 1 } },
        },
    };
}

/** A Google OAuth access token for FCM from the service account, cached until shortly before it expires. */
async function accessToken(account) {
    const now = Math.floor(Date.now() / 1000);
    if (token && token.expiresAt - 60 > now) return token.value;
    const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
    const unsigned = `${b64({ alg: "RS256", typ: "JWT" })}.${b64({
        iss: account.client_email,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
    })}`;
    const signature = createSign("RSA-SHA256").update(unsigned).sign(account.private_key, "base64url");
    const res = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${signature}` }),
    });
    if (!res.ok) throw new Error(`token request failed: ${res.status} ${await res.text()}`);
    const json = await res.json();
    token = { value: json.access_token, expiresAt: now + json.expires_in };
    return token.value;
}

function reply(statusCode, message) {
    return { statusCode, headers: { "content-type": "text/plain" }, body: message };
}
