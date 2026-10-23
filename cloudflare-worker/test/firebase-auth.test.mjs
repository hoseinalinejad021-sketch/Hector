import test from "node:test";
import assert from "node:assert/strict";
import { verifyFirebaseIdToken } from "../src/firebase-auth.js";

function base64url(value) {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

test("Firebase token verification accepts a correctly signed token for the configured project", async () => {
  const originalFetch = globalThis.fetch;
  try {
    const pair = await crypto.subtle.generateKey({
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256"
    }, true, ["sign", "verify"]);
    const jwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
    Object.assign(jwk, { kid: "test-key", alg: "RS256", use: "sig" });
    globalThis.fetch = async () => new Response(JSON.stringify({ keys: [jwk] }), {
      status: 200,
      headers: { "Content-Type": "application/json", "Cache-Control": "max-age=60" }
    });

    const now = Math.floor(Date.now() / 1000);
    const head = base64url(JSON.stringify({ alg: "RS256", kid: "test-key", typ: "JWT" }));
    const claims = base64url(JSON.stringify({
      aud: "hector-9b819",
      iss: "https://securetoken.google.com/hector-9b819",
      sub: "firebase-user-123",
      iat: now,
      exp: now + 3600
    }));
    const signingInput = `${head}.${claims}`;
    const signature = await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5", pair.privateKey, new TextEncoder().encode(signingInput)
    );
    const token = `${signingInput}.${base64url(new Uint8Array(signature))}`;
    const request = new Request("https://hector.test/tasks", {
      headers: { Authorization: `Bearer ${token}` }
    });

    assert.deepEqual(await verifyFirebaseIdToken(request, "hector-9b819"), { uid: "firebase-user-123" });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("Firebase token verification rejects wrong audience before network access", async () => {
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async () => { throw new Error("must not fetch keys for invalid claims"); };
    const head = base64url(JSON.stringify({ alg: "RS256", kid: "test-key" }));
    const claims = base64url(JSON.stringify({
      aud: "another-project",
      iss: "https://securetoken.google.com/another-project",
      sub: "firebase-user-123",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600
    }));
    const request = new Request("https://hector.test/tasks", {
      headers: { Authorization: `Bearer ${head}.${claims}.c2ln` }
    });
    assert.equal(await verifyFirebaseIdToken(request, "hector-9b819"), null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
