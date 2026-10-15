// Run with: node --test server/wake
import { test } from "node:test";
import assert from "node:assert/strict";
import { allow, handler, validate, wakeMessage } from "./index.mjs";

const topic = "w" + "0123456789abcdef".repeat(2);

test("accepts a topic and rejects anything else", () => {
    assert.equal(validate({ topic }), null);
    assert.equal(validate({ topic, urgent: true }), null);
    assert.equal(validate({ topic: "family-shopping" }), "bad topic");
    assert.equal(validate({ topic: topic + "0" }), "bad topic");
    assert.equal(validate({ topic, urgent: "yes" }), "urgent must be true or false");
    assert.equal(validate(null), "expected an object");
});

test("one wake per topic every few seconds", () => {
    const t = "w" + "f".repeat(32);
    assert.equal(allow(t, 1_000_000), true);
    assert.equal(allow(t, 1_001_000), false);
    assert.equal(allow(t, 1_006_000), true);
});

test("the push carries no content, only the topic", () => {
    const m = wakeMessage(topic, false);
    assert.deepEqual(m.data, { wake: topic });
    assert.equal(m.android.priority, "NORMAL");
    assert.equal(wakeMessage(topic, true).android.priority, "HIGH");
});

test("bad requests are refused before anything is sent", async () => {
    assert.equal((await handler({ body: "nope" })).statusCode, 400);
    assert.equal((await handler({ body: JSON.stringify({ topic: "x" }) })).statusCode, 400);
    assert.equal((await handler({ requestContext: { http: { method: "GET" } } })).statusCode, 405);
});
