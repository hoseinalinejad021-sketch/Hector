const MAX_TASKS_PER_USER = 20;
const MAX_PROMPT_CHARS = 4000;
const MAX_ANSWER_CHARS = 8000;
const MAX_ATTEMPTS = 2;

export class HectorTaskQueue {
  constructor(state, env) {
    this.state = state;
    this.env = env;
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/tasks") {
      let body;
      try { body = await request.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
      const prompt = typeof body.prompt === "string" ? body.prompt.trim() : "";
      const idempotencyKey = typeof body.idempotencyKey === "string" ? body.idempotencyKey.trim() : "";
      if (!prompt || prompt.length > MAX_PROMPT_CHARS) {
        return json({ error: "prompt must contain 1-4000 characters" }, 400);
      }
      if (idempotencyKey.length > 128) return json({ error: "idempotencyKey is too long" }, 400);

      if (idempotencyKey) {
        const existingId = await this.state.storage.get(`idempotency:${idempotencyKey}`);
        if (existingId) {
          const existing = await this.state.storage.get(`task:${existingId}`);
          if (existing) return json({ task: publicTask(existing), deduplicated: true }, 200);
        }
      }

      const entries = await this.state.storage.list({ prefix: "task:" });
      const pending = [...entries.values()].filter(task =>
        task.status === "queued" || task.status === "processing"
      ).length;
      if (pending >= MAX_TASKS_PER_USER) {
        return json({ error: "Too many pending tasks; try again after a task finishes" }, 429);
      }

      const now = Date.now();
      const task = {
        id: crypto.randomUUID(),
        prompt,
        status: "queued",
        createdAt: now,
        updatedAt: now,
        attempts: 0
      };
      await this.state.storage.put(`task:${task.id}`, task);
      if (idempotencyKey) await this.state.storage.put(`idempotency:${idempotencyKey}`, task.id);
      await this.state.storage.setAlarm(now + 1000);
      return json({ task: publicTask(task), deduplicated: false }, 202);
    }

    const match = url.pathname.match(/^\/tasks\/([0-9a-f-]{36})$/i);
    if (request.method === "GET" && match) {
      const task = await this.state.storage.get(`task:${match[1]}`);
      return task ? json({ task: publicTask(task) }, 200) : json({ error: "Task not found" }, 404);
    }
    return json({ error: "Not found" }, 404);
  }

  async alarm() {
    if (!this.env.OPENAI_API_KEY) {
      await this.failNextQueuedTask("Cloud AI provider is not configured.");
      return;
    }

    const entries = await this.state.storage.list({ prefix: "task:" });
    const next = [...entries.values()]
      .filter(task => task.status === "queued")
      .sort((a, b) => a.createdAt - b.createdAt)[0];

    if (!next) return;
    next.status = "processing";
    next.attempts += 1;
    next.updatedAt = Date.now();
    await this.state.storage.put(`task:${next.id}`, next);

    try {
      const model = typeof this.env.OPENAI_MODEL === "string" && this.env.OPENAI_MODEL
        ? this.env.OPENAI_MODEL
        : "gpt-4.1-mini";
      const response = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.env.OPENAI_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model,
          input: [{ role: "user", content: [{ type: "input_text", text: next.prompt }] }]
        })
      });

      if (!response.ok) throw new Error("AI provider request failed");
      const data = await response.json();
      const answer = Array.isArray(data.output)
        ? data.output.flatMap(item => Array.isArray(item.content) ? item.content : [])
            .filter(item => item.type === "output_text")
            .map(item => typeof item.text === "string" ? item.text : "")
            .join("\n").trim()
        : "";
      if (!answer) throw new Error("AI provider returned no text");

      next.status = "completed";
      next.answer = answer.slice(0, MAX_ANSWER_CHARS);
      next.model = model;
      next.updatedAt = Date.now();
      await this.state.storage.put(`task:${next.id}`, next);
    } catch {
      next.updatedAt = Date.now();
      if (next.attempts < MAX_ATTEMPTS) {
        next.status = "queued";
        await this.state.storage.put(`task:${next.id}`, next);
        await this.state.storage.setAlarm(Date.now() + 5000);
        return;
      }
      next.status = "failed";
      next.error = "Cloud task failed after retry; check provider configuration and quota.";
      await this.state.storage.put(`task:${next.id}`, next);
    }

    const remaining = await this.state.storage.list({ prefix: "task:" });
    if ([...remaining.values()].some(task => task.status === "queued")) {
      await this.state.storage.setAlarm(Date.now() + 1000);
    }
  }

  async failNextQueuedTask(message) {
    const entries = await this.state.storage.list({ prefix: "task:" });
    const next = [...entries.values()]
      .filter(task => task.status === "queued")
      .sort((a, b) => a.createdAt - b.createdAt)[0];
    if (!next) return;
    next.status = "failed";
    next.error = message;
    next.updatedAt = Date.now();
    await this.state.storage.put(`task:${next.id}`, next);
    if ([...entries.values()].some(task => task.status === "queued" && task.id !== next.id)) {
      await this.state.storage.setAlarm(Date.now() + 1000);
    }
  }
}

function publicTask(task) {
  const result = {
    id: task.id,
    status: task.status,
    createdAt: task.createdAt,
    updatedAt: task.updatedAt,
    attempts: task.attempts
  };
  if (task.answer) result.answer = task.answer;
  if (task.model) result.model = task.model;
  if (task.error) result.error = task.error;
  return result;
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }
  });
}
