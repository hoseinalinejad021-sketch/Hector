export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    if (url.pathname !== "/chat" || request.method !== "POST") {
      return json({ error: "Not found" }, 404);
    }
    if (!env.OPENAI_API_KEY) {
      return json({ error: "Server is missing OPENAI_API_KEY" }, 503);
    }
    let body;
    try { body = await request.json(); } catch {
      return json({ error: "Invalid JSON" }, 400);
    }
    const message = typeof body.message === "string" ? body.message.trim() : "";
    if (!message || message.length > 8000) {
      return json({ error: "message must contain 1-8000 characters" }, 400);
    }
    const model = typeof env.OPENAI_MODEL === "string" && env.OPENAI_MODEL
      ? env.OPENAI_MODEL : "gpt-4.1-mini";
    const upstream = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model,
        input: [{ role: "user", content: [{ type: "input_text", text: message }] }]
      })
    });
    const raw = await upstream.text();
    if (!upstream.ok) {
      return json({ error: "AI provider request failed", status: upstream.status }, 502);
    }
    let data;
    try { data = JSON.parse(raw); } catch {
      return json({ error: "Invalid provider response" }, 502);
    }
    const answer = Array.isArray(data.output)
      ? data.output.flatMap(item => Array.isArray(item.content) ? item.content : [])
          .filter(item => item.type === "output_text")
          .map(item => item.text || "").join("\n")
      : "";
    if (!answer) return json({ error: "No text returned by AI provider" }, 502);
    return json({ answer, model });
  }
};

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  };
}
function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: corsHeaders() });
}
