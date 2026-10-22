import test, { beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const cacheEntries = new Map();
const originalFetch = globalThis.fetch;
globalThis.caches = {
  default: {
    async match(request) {
      const response = cacheEntries.get(request.url);
      return response ? response.clone() : null;
    },
    async put(request, response) {
      cacheEntries.set(request.url, response.clone());
    }
  }
};

beforeEach(() => cacheEntries.clear());
afterEach(() => { globalThis.fetch = originalFetch; });

const env = { HECTOR_GATE_TOKEN: "test-gate-token", OPENAI_API_KEY: "test-provider-key" };

function post(path, body, token = "test-gate-token", ip = "203.0.113.10") {
  const headers = { "Content-Type": "application/json", "CF-Connecting-IP": ip };
  if (token !== null) headers.Authorization = `Bearer ${token}`;
  return new Request(`https://hector.test${path}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body)
  });
}

test("chat rejects a missing gate token before calling the provider", async () => {
  let providerCalled = false;
  globalThis.fetch = async () => { providerCalled = true; throw new Error("unexpected provider call"); };

  const response = await worker.fetch(post("/chat", { message: "hello" }, null), env);
  assert.equal(response.status, 401);
  assert.equal(providerCalled, false);
});

test("chat fails closed when the gate token is not configured", async () => {
  const response = await worker.fetch(post("/chat", { message: "hello" }), {
    OPENAI_API_KEY: "test-provider-key"
  });
  assert.equal(response.status, 503);
});

test("chat rejects a wrong token without spending provider quota", async () => {
  let providerCalled = false;
  globalThis.fetch = async () => { providerCalled = true; throw new Error("unexpected provider call"); };

  const response = await worker.fetch(post("/chat", { message: "hello" }, "wrong-token"), env);
  assert.equal(response.status, 401);
  assert.equal(providerCalled, false);
});

test("chat returns provider text but never returns the provider secret", async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(String(url), "https://api.openai.com/v1/responses");
    assert.equal(options.headers.Authorization, "Bearer test-provider-key");
    return new Response(JSON.stringify({
      output: [{ content: [{ type: "output_text", text: "Hector is ready." }] }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  const response = await worker.fetch(post("/chat", { message: "hello" }), env);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.answer, "Hector is ready.");
  assert.equal(body.model, "gpt-4.1-mini");
  assert.equal(JSON.stringify(body).includes("test-provider-key"), false);
});

test("chat rejects oversized messages without calling the provider", async () => {
  let providerCalled = false;
  globalThis.fetch = async () => { providerCalled = true; throw new Error("unexpected provider call"); };

  const response = await worker.fetch(post("/chat", { message: "x".repeat(8001) }), env);
  assert.equal(response.status, 400);
  assert.equal(providerCalled, false);
});

test("chat applies the documented best-effort per-IP request ceiling", async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({
    output: [{ content: [{ type: "output_text", text: "ok" }] }]
  }), { status: 200, headers: { "Content-Type": "application/json" } });

  for (let i = 0; i < 12; i++) {
    const response = await worker.fetch(post("/chat", { message: "hello" }), env);
    assert.equal(response.status, 200, `request ${i + 1} should be allowed`);
  }
  const limited = await worker.fetch(post("/chat", { message: "hello" }), env);
  assert.equal(limited.status, 429);
});

test("live session rejects an incorrect gate token before provider access", async () => {
  let providerCalled = false;
  globalThis.fetch = async () => { providerCalled = true; throw new Error("unexpected provider call"); };

  const response = await worker.fetch(post("/live/session", { sdp: "v=0" }, "wrong-token"), env);
  assert.equal(response.status, 401);
  assert.equal(providerCalled, false);
});
