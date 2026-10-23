import test from "node:test";
import assert from "node:assert/strict";
import { HectorTaskQueue } from "../src/task-queue.js";

function createQueue(env = { OPENAI_API_KEY: "test-provider-key", OPENAI_MODEL: "gpt-test" }) {
  const values = new Map();
  let alarmAt = null;
  const state = {
    storage: {
      async get(key) { return values.get(key); },
      async put(key, value) { values.set(key, structuredClone(value)); },
      async list({ prefix = "" } = {}) {
        return new Map([...values.entries()].filter(([key]) => key.startsWith(prefix))
          .map(([key, value]) => [key, structuredClone(value)]));
      },
      async setAlarm(value) { alarmAt = value; }
    }
  };
  return {
    queue: new HectorTaskQueue(state, env),
    values,
    get alarmAt() { return alarmAt; }
  };
}

function post(body) {
  return new Request("https://queue.internal/tasks", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
}

test("queue rejects empty and oversized prompts", async () => {
  const { queue, values } = createQueue();
  assert.equal((await queue.fetch(post({ prompt: "  " }))).status, 400);
  assert.equal((await queue.fetch(post({ prompt: "x".repeat(4001) }))).status, 400);
  assert.equal(values.size, 0);
});

test("queue deduplicates the same idempotency key", async () => {
  const { queue, values } = createQueue();
  const first = await queue.fetch(post({ prompt: "Check this plan", idempotencyKey: "client-req-1" }));
  const second = await queue.fetch(post({ prompt: "Check this plan again", idempotencyKey: "client-req-1" }));
  assert.equal(first.status, 202);
  assert.equal(second.status, 200);
  const firstBody = await first.json();
  const secondBody = await second.json();
  assert.equal(firstBody.task.id, secondBody.task.id);
  assert.equal(secondBody.deduplicated, true);
  assert.equal(values.size, 2);
});

test("task status response does not disclose the stored prompt", async () => {
  const { queue } = createQueue();
  const created = await queue.fetch(post({ prompt: "private prompt text" }));
  const body = await created.json();
  const response = await queue.fetch(new Request(`https://queue.internal/tasks/${body.task.id}`));
  const status = await response.json();
  assert.equal(response.status, 200);
  assert.equal(status.task.status, "queued");
  assert.equal(JSON.stringify(status).includes("private prompt text"), false);
});

test("alarm executes queued task and stores a bounded public result", async () => {
  const originalFetch = globalThis.fetch;
  try {
    const { queue } = createQueue();
    globalThis.fetch = async (url, options) => {
      assert.equal(String(url), "https://api.openai.com/v1/responses");
      assert.equal(options.headers.Authorization, "Bearer test-provider-key");
      return new Response(JSON.stringify({
        output: [{ content: [{ type: "output_text", text: "Hector task completed." }] }]
      }), { status: 200 });
    };
    const created = await queue.fetch(post({ prompt: "Summarize the sample" }));
    const body = await created.json();
    await queue.alarm();
    const response = await queue.fetch(new Request(`https://queue.internal/tasks/${body.task.id}`));
    const result = await response.json();
    assert.equal(result.task.status, "completed");
    assert.equal(result.task.answer, "Hector task completed.");
    assert.equal(result.task.model, "gpt-test");
    assert.equal("prompt" in result.task, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("alarm retries one provider failure, then completes when provider recovers", async () => {
  const originalFetch = globalThis.fetch;
  try {
    const { queue, values } = createQueue();
    const created = await queue.fetch(post({ prompt: "Retry safely" }));
    const body = await created.json();
    let calls = 0;
    globalThis.fetch = async () => {
      calls += 1;
      if (calls === 1) throw new Error("simulated temporary outage");
      return new Response(JSON.stringify({
        output: [{ content: [{ type: "output_text", text: "Recovered." }] }]
      }), { status: 200 });
    };
    await queue.alarm();
    assert.equal(values.get(`task:${body.task.id}`).status, "queued");
    await queue.alarm();
    assert.equal(values.get(`task:${body.task.id}`).status, "completed");
    assert.equal(calls, 2);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("queue fails closed when provider secret is missing", async () => {
  const { queue } = createQueue({});
  const created = await queue.fetch(post({ prompt: "Do not run without a secret" }));
  const body = await created.json();
  await queue.alarm();
  const response = await queue.fetch(new Request(`https://queue.internal/tasks/${body.task.id}`));
  const result = await response.json();
  assert.equal(result.task.status, "failed");
  assert.equal(result.task.error, "Cloud AI provider is not configured.");
});
