const SYMBOLS = ["BTCUSDT", "ETHUSDT", "SOLUSDT"];
const STARTING_CASH = 10000;
const MAX_POSITION_FRACTION = 0.05;
const DAILY_LOSS_LIMIT = 0.02;
const FEE_RATE = 0.001;
const BINANCE = "https://api.binance.com/api/v3/klines";

export function ema(values, period) {
  if (!Array.isArray(values) || values.length < period || period < 1) return null;
  const alpha = 2 / (period + 1);
  let result = values.slice(0, period).reduce((a, b) => a + b, 0) / period;
  for (const value of values.slice(period)) result = value * alpha + result * (1 - alpha);
  return result;
}
export function getSignal(closes) {
  if (!Array.isArray(closes) || closes.length < 52 || closes.some(v => !Number.isFinite(v) || v <= 0)) return { signal: "insufficient_data" };
  const prior = closes.slice(0, -1);
  const prevFast = ema(prior.slice(-51), 20), prevSlow = ema(prior.slice(-51), 50);
  const fast = ema(closes.slice(-50), 20), slow = ema(closes.slice(-50), 50);
  if ([prevFast, prevSlow, fast, slow].some(v => v === null)) return { signal: "insufficient_data" };
  if (prevFast <= prevSlow && fast > slow) return { signal: "buy" };
  if (prevFast >= prevSlow && fast < slow) return { signal: "sell" };
  return { signal: "hold" };
}
export class HectorCryptoPaperEngine {
  constructor(ctx, env) { this.ctx = ctx; this.env = env; }
  async fetch(request) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/status") return json(await this.readState());
    if (request.method === "POST" && url.pathname === "/start") {
      const state = await this.readState();
      if (state.emergencyStop) return json({ error: "HIC-STOP-01 is engaged; clear stop before starting paper mode" }, 423);
      state.enabled = true; state.updatedAt = new Date().toISOString();
      await this.ctx.storage.put("state", state);
      await this.ctx.storage.setAlarm(Date.now() + 1000);
      return json({ ok: true, mode: "paper-only", state });
    }
    if (request.method === "POST" && url.pathname === "/stop") {
      const state = await this.readState(); state.enabled = false; state.emergencyStop = true;
      state.updatedAt = new Date().toISOString(); await this.ctx.storage.put("state", state);
      return json({ ok: true, stopped: true, control: "HIC-STOP-01", state });
    }
    if (request.method === "POST" && url.pathname === "/reset-stop") {
      const state = await this.readState(); state.emergencyStop = false; state.enabled = false;
      state.updatedAt = new Date().toISOString(); await this.ctx.storage.put("state", state);
      return json({ ok: true, stopped: false, enabled: false, note: "Paper mode remains disabled until /start", state });
    }
    if (request.method === "POST" && url.pathname === "/scan") return json(await this.scan());
    return json({ error: "Not found" }, 404);
  }
  async alarm() {
    await this.scan();
    const state = await this.readState();
    if (state.enabled && !state.emergencyStop) await this.ctx.storage.setAlarm(Date.now() + 15 * 60 * 1000);
  }
  async readState() {
    return (await this.ctx.storage.get("state")) || {
      mode: "paper-only", enabled: false, emergencyStop: true, cash: STARTING_CASH,
      positions: {}, lastPrices: {}, trades: [], signals: [], startingDayEquity: STARTING_CASH,
      day: new Date().toISOString().slice(0, 10), realizedPnl: 0, createdAt: new Date().toISOString()
    };
  }
  async scan() {
    const state = await this.readState(), now = new Date(), day = now.toISOString().slice(0, 10);
    if (state.emergencyStop || !state.enabled) {
      state.lastScan = { at: now.toISOString(), skipped: true, reason: state.emergencyStop ? "HIC-STOP-01 engaged" : "paper automation disabled" };
      await this.ctx.storage.put("state", state);
      return { ok: true, ...state.lastScan, mode: "paper-only" };
    }

    // Refresh all available marks before checking the daily loss guard or trading.
    const market = [];
    for (const symbol of SYMBOLS) {
      try {
        const response = await fetch(BINANCE + "?symbol=" + symbol + "&interval=1h&limit=100", { headers: { Accept: "application/json" } });
        if (!response.ok) throw new Error("market data HTTP " + response.status);
        const candles = await response.json();
        if (!Array.isArray(candles) || candles.length < 52) throw new Error("insufficient candle data");
        const closes = candles.slice(0, -1).map(c => Number(c[4]));
        const price = closes[closes.length - 1], signal = getSignal(closes).signal;
        if (!Number.isFinite(price) || price <= 0) throw new Error("invalid market price");
        state.lastPrices[symbol] = price;
        market.push({ symbol, price, signal, at: now.toISOString() });
      } catch (error) {
        market.push({ symbol, signal: "unavailable", error: String(error?.message || error).slice(0, 160), at: now.toISOString() });
      }
    }

    const refreshedEquity = markEquity(state);
    if (state.day !== day) {
      state.day = day;
      state.startingDayEquity = refreshedEquity;
    }
    if (refreshedEquity <= Number(state.startingDayEquity || STARTING_CASH) * (1 - DAILY_LOSS_LIMIT)) {
      state.enabled = false;
      state.emergencyStop = true;
      state.signals = market;
      state.lastScan = { at: now.toISOString(), skipped: true, reason: "daily paper drawdown limit reached", drawdownLimit: DAILY_LOSS_LIMIT, equity: refreshedEquity };
      state.updatedAt = now.toISOString();
      await this.ctx.storage.put("state", state);
      return { ok: true, ...state.lastScan, control: "HIC-STOP-01" };
    }

    for (const item of market) {
      const { symbol, price, signal } = item;
      if (signal === "buy" && !state.positions[symbol]) {
        const notional = Math.min(Math.max(0, state.cash / (1 + FEE_RATE)), Math.max(0, markEquity(state) * MAX_POSITION_FRACTION));
        if (notional > 10) {
          const fee = notional * FEE_RATE;
          state.cash -= notional + fee;
          state.positions[symbol] = { quantity: notional / price, entryPrice: price, entryFee: fee, openedAt: now.toISOString() };
          state.trades.push({ side: "BUY", symbol, price, notional, fee, mode: "paper", at: now.toISOString() });
        }
      } else if (signal === "sell" && state.positions[symbol]) {
        const pos = state.positions[symbol], proceeds = pos.quantity * price, fee = proceeds * FEE_RATE;
        const pnl = proceeds - fee - pos.quantity * pos.entryPrice - pos.entryFee;
        state.cash += proceeds - fee;
        state.realizedPnl += pnl;
        state.trades.push({ side: "SELL", symbol, price, quantity: pos.quantity, fee, pnl, mode: "paper", at: now.toISOString() });
        delete state.positions[symbol];
      }
    }
    state.signals = market;
    state.lastScan = { at: now.toISOString(), skipped: false, mode: "paper-only" };
    state.updatedAt = now.toISOString();
    await this.ctx.storage.put("state", state);
    await this.ctx.storage.setAlarm(Date.now() + 15 * 60 * 1000);
    return { ok: true, mode: "paper-only", equity: markEquity(state), cash: state.cash, realizedPnl: state.realizedPnl, signals: market };
  }
}
function markEquity(state) {
  let equity = Number(state.cash || 0);
  for (const [symbol, pos] of Object.entries(state.positions || {})) equity += pos.quantity * Number(state.lastPrices?.[symbol] || pos.entryPrice || 0);
  return equity;
}
function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}
