import { HectorCryptoPaperEngine } from "./crypto-paper-engine.js";
import { verifyFirebaseIdToken } from "./firebase-auth.js";
import { HectorTaskQueue } from "./task-queue.js";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname.startsWith("/crypto/paper")) {
      if (!env.HECTOR_GATE_TOKEN) return json({ error: "Worker secret HECTOR_GATE_TOKEN is required" }, 503);
      if (request.headers.get("Authorization") !== `Bearer ${env.HECTOR_GATE_TOKEN}`) return json({ error: "Unauthorized" }, 401);
      if (!env.CRYPTO_PAPER) return json({ error: "Crypto paper engine is not configured" }, 503);
      const action = url.pathname.slice("/crypto/paper".length) || "/status";
      const allowed = (request.method === "GET" && action === "/status") ||
        (request.method === "POST" && ["/start", "/stop", "/reset-stop", "/scan"].includes(action));
      if (!allowed) return json({ error: "Method or path not allowed" }, 405);
      const engine = env.CRYPTO_PAPER.get(env.CRYPTO_PAPER.idFromName("global-paper-portfolio"));
      const internal = new Request("https://crypto-paper.internal" + action, {
        method: request.method,
        headers: { "Content-Type": "application/json" },
        body: request.method === "POST" ? "{}" : undefined
      });
      const response = await engine.fetch(internal);
      return new Response(await response.text(), { status: response.status, headers: { ...corsHeaders(), "Cache-Control": "no-store" } });
    }
    const isTaskCreate = url.pathname === "/tasks" && request.method === "POST";
    const taskReadMatch = url.pathname.match(/^\/tasks\/([0-9a-f-]{36})$/i);
    if (isTaskCreate || (taskReadMatch && request.method === "GET")) {
      if (!env.FIREBASE_PROJECT_ID || !env.TASK_QUEUE) {
        return json({ error: "Cloud task queue is not configured" }, 503);
      }
      const identity = await verifyFirebaseIdToken(request, env.FIREBASE_PROJECT_ID);
      if (!identity) return json({ error: "Valid Firebase sign-in is required" }, 401);
      const queueId = env.TASK_QUEUE.idFromName(identity.uid);
      const queue = env.TASK_QUEUE.get(queueId);
      const internalRequest = new Request(`https://task-queue.internal${url.pathname}`, {
        method: request.method,
        headers: { "Content-Type": "application/json" },
        body: isTaskCreate ? await request.text() : undefined
      });
      const queuedResponse = await queue.fetch(internalRequest);
      return new Response(await queuedResponse.text(), {
        status: queuedResponse.status,
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          "Cache-Control": "no-store",
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Headers": "Authorization, Content-Type",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
        }
      });
    }
    if (url.pathname === "/live" && request.method === "GET") {
      return new Response(liveHtml(), {
        headers: {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store",
          "Content-Security-Policy": "default-src 'self' https://api.openai.com; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self' https://api.openai.com; media-src blob:; img-src 'self' data:"
        }
      });
    }
    if (url.pathname === "/live/session" && request.method === "POST") {
      if (!env.HECTOR_GATE_TOKEN || !env.OPENAI_API_KEY) {
        return json({ error: "Worker secrets OPENAI_API_KEY and HECTOR_GATE_TOKEN are required" }, 503);
      }
      if (request.headers.get("Authorization") !== `Bearer ${env.HECTOR_GATE_TOKEN}`) {
        return json({ error: "Unauthorized" }, 401);
      }
      const clientIp = request.headers.get("CF-Connecting-IP") || "unknown";
      const rateKey = new Request(`${url.origin}/__hector_rate_limit/live/${encodeURIComponent(clientIp)}`);
      const cache = caches.default;
      const cached = await cache.match(rateKey);
      const count = cached ? Number(await cached.text()) : 0;
      if (count >= 12) return json({ error: "Rate limit exceeded; wait one minute" }, 429);
      await cache.put(rateKey, new Response(String(count + 1), {
        headers: { "Cache-Control": "max-age=60" }
      }));
      let body;
      try { body = await request.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
      const sdp = typeof body.sdp === "string" ? body.sdp : "";
      if (!sdp || sdp.length > 200000) return json({ error: "A valid SDP offer is required" }, 400);
      const upstream = await fetch("https://api.openai.com/v1/live/sessions", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          session: {
            model: "gpt-live-1",
            instructions: "تو هیکتور، دستیار شخصی فارسی‌زبان هستی. فارسی روشن و مختصر صحبت کن. اگر درخواست مبهم است سؤال روشن‌کننده بپرس. هرگز ادعا نکن کاری انجام شده مگر اینکه نتیجهٔ آن تأیید شده باشد. هیچ اقدام حساس یا تغییردهنده‌ای را بدون تأیید صریح کاربر انجام نده.",
            audio: { output: { voice: "marin" } }
          },
          transport: { type: "webrtc", sdp }
        })
      });
      const raw = await upstream.text();
      if (!upstream.ok) {
        return json({ error: "Live provider session creation failed", status: upstream.status, detail: raw.slice(0, 1200) }, 502);
      }
      let data;
      try { data = JSON.parse(raw); } catch { return json({ error: "Invalid provider response" }, 502); }
      const answerSdp = data?.transport?.sdp || data?.sdp || "";
      if (!answerSdp) return json({ error: "Provider response did not contain an SDP answer" }, 502);
      return json({ sdp: answerSdp });
    }
    if (url.pathname === "/chat" && request.method === "POST") {
      // This endpoint spends the server-side provider quota. Never expose it as
      // an unauthenticated public proxy.
      if (!env.HECTOR_GATE_TOKEN) {
        return json({ error: "Worker secret HECTOR_GATE_TOKEN is required" }, 503);
      }
      if (request.headers.get("Authorization") !== `Bearer ${env.HECTOR_GATE_TOKEN}`) {
        return json({ error: "Unauthorized" }, 401);
      }

      // Best-effort per-IP throttling, separate from the live-session endpoint.
      const clientIp = request.headers.get("CF-Connecting-IP") || "unknown";
      const rateKey = new Request(`${url.origin}/__hector_rate_limit/chat/${encodeURIComponent(clientIp)}`);
      const cache = caches.default;
      const cached = await cache.match(rateKey);
      const count = cached ? Number(await cached.text()) : 0;
      if (count >= 12) return json({ error: "Rate limit exceeded; wait one minute" }, 429);
      await cache.put(rateKey, new Response(String(count + 1), {
        headers: { "Cache-Control": "max-age=60" }
      }));

      if (!env.OPENAI_API_KEY) return json({ error: "Server is missing OPENAI_API_KEY" }, 503);
      let body;
      try { body = await request.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
      const message = typeof body.message === "string" ? body.message.trim() : "";
      if (!message || message.length > 8000) return json({ error: "message must contain 1-8000 characters" }, 400);
      const model = typeof env.OPENAI_MODEL === "string" && env.OPENAI_MODEL ? env.OPENAI_MODEL : "gpt-4.1-mini";
      const upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: { "Authorization": `Bearer ${env.OPENAI_API_KEY}`, "Content-Type": "application/json" },
        body: JSON.stringify({ model, input: [{ role: "user", content: [{ type: "input_text", text: message }] }] })
      });
      const raw = await upstream.text();
      if (!upstream.ok) return json({ error: "AI provider request failed", status: upstream.status }, 502);
      let data;
      try { data = JSON.parse(raw); } catch { return json({ error: "Invalid provider response" }, 502); }
      const answer = Array.isArray(data.output)
        ? data.output.flatMap(item => Array.isArray(item.content) ? item.content : []).filter(item => item.type === "output_text").map(item => item.text || "").join("\n")
        : "";
      if (!answer) return json({ error: "No text returned by AI provider" }, 502);
      return json({ answer, model });
    }
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
    return json({ error: "Not found" }, 404);
  }
};

function liveHtml() {
  return `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>هیکتور | گفت‌وگوی زنده</title>
  <style>body{font-family:system-ui;background:#0f172a;color:#f8fafc;margin:0;padding:24px}main{max-width:600px;margin:auto}h1{font-size:26px}p{line-height:1.8;color:#cbd5e1}input,button{box-sizing:border-box;width:100%;padding:14px;margin:8px 0;border-radius:10px;border:1px solid #475569;font-size:16px}input{background:#1e293b;color:white}button{background:#2563eb;color:white;font-weight:700}button.stop{background:#991b1b}#status{padding:12px;background:#1e293b;border-radius:10px;min-height:24px;white-space:pre-wrap}audio{width:100%}</style></head><body><main><h1>هیکتور — گفت‌وگوی صوتی زنده</h1><p>کلید اصلی OpenAI در سرور می‌ماند. رمز دروازه را که خودت در تنظیمات Worker تعریف کرده‌ای وارد کن.</p>
  <input id="token" type="password" autocomplete="off" placeholder="رمز دروازهٔ هیکتور (HECTOR_GATE_TOKEN)">
  <button id="start">شروع مکالمهٔ زنده</button><button id="stop" class="stop" disabled>پایان مکالمه</button><div id="status">آمادهٔ اتصال؛ با زدن شروع، اجازهٔ میکروفون را بده.</div><audio id="remoteAudio" autoplay playsinline></audio>
  <script>
  const tokenInput=document.getElementById('token'),startBtn=document.getElementById('start'),stopBtn=document.getElementById('stop'),statusEl=document.getElementById('status'),audioEl=document.getElementById('remoteAudio');
  let pc=null,dc=null,stream=null;
  function status(t){statusEl.textContent=t}
  async function stop(){if(dc){try{dc.close()}catch{}dc=null}if(pc){try{pc.close()}catch{}pc=null}if(stream){stream.getTracks().forEach(t=>t.stop());stream=null}audioEl.srcObject=null;startBtn.disabled=false;stopBtn.disabled=true;status('مکالمه پایان یافت.')}
  startBtn.addEventListener('click',async()=>{
    const token=tokenInput.value.trim();if(!token){status('رمز دروازه را وارد کن.');return}
    startBtn.disabled=true;status('در حال درخواست دسترسی میکروفون…');
    try{
      stream=await navigator.mediaDevices.getUserMedia({audio:true});
      pc=new RTCPeerConnection();pc.ontrack=e=>{audioEl.srcObject=e.streams[0];audioEl.play().catch(()=>{})};
      stream.getTracks().forEach(t=>pc.addTrack(t,stream));
      dc=pc.createDataChannel('oai-events');
      dc.onopen=()=>status('متصل شدی؛ صحبت کن.');dc.onmessage=e=>{try{const v=JSON.parse(e.data);if(v.type==='error')status('خطای مکالمه: '+(v.error?.message||'نامشخص'));}catch{}};
      pc.onconnectionstatechange=()=>{if(pc&&['failed','disconnected','closed'].includes(pc.connectionState))status('اتصال قطع شد: '+pc.connectionState)};
      const offer=await pc.createOffer();await pc.setLocalDescription(offer);
      await new Promise((resolve,reject)=>{if(pc.iceGatheringState==='complete')return resolve();const timer=setTimeout(()=>reject(new Error('زمان اتصال شبکه تمام شد')),12000);pc.addEventListener('icegatheringstatechange',()=>{if(pc&&pc.iceGatheringState==='complete'){clearTimeout(timer);resolve()}})});
      status('در حال اتصال امن به GPT-Live…');
      const res=await fetch('/live/session',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+token},body:JSON.stringify({sdp:pc.localDescription.sdp})});
      const data=await res.json();if(!res.ok)throw new Error(data.error||('HTTP '+res.status));
      await pc.setRemoteDescription({type:'answer',sdp:data.sdp});stopBtn.disabled=false;status('در حال برقراری مکالمه…');
    }catch(e){await stop();status('اتصال برقرار نشد: '+(e.message||'خطای نامشخص')+'\nرمز دروازه، تنظیمات Worker، دسترسی میکروفون و مجوز Live API را بررسی کن.')}
  });
  window.hectorStop=stop;stopBtn.addEventListener('click',stop);window.addEventListener('pagehide',stop);
  </script></main></body></html>`;
}
function corsHeaders() {
  return { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "POST, OPTIONS", "Access-Control-Allow-Headers": "Content-Type, Authorization", "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };
}
function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: corsHeaders() });
}

export { HectorTaskQueue };

export { HectorCryptoPaperEngine };
