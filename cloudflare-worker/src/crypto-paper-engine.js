const SYMBOLS = ["BTCUSDT", "ETHUSDT", "SOLUSDT"];
const STARTING_CASH = 10000;
const MAX_POSITION_FRACTION = 0.05;
const DAILY_LOSS_LIMIT = 0.02;
const FEE_RATE = 0.001;
const BINANCE = "https://api.binance.com/api/v3/klines";

export function ema(values, period) {
  if (!Array.isArray(values) || !Number.isInteger(period) || period < 1 || values.length < period ||
      values.some(value => !Number.isFinite(value) || value <= 0)) return null;
  const alpha = 2 / (period + 1);
  let result = values.slice(0, period).reduce((sum, value) => sum + value, 0) / period;
  for (const value of values.slice(period)) result = value * alpha + result * (1 - alpha);
  return result;
}

export function getSignal(closes) {
  if (!Array.isArray(closes) || closes.length < 52 ||
      closes.some(value => !Number.isFinite(value) || value <= 0)) return { signal: "insufficient_data" };
  const previous = closes.slice(0, -1);
  const prevFast = ema(previous, 20), prevSlow = ema(previous, 50);
  const fast = ema(closes, 20), slow = ema(closes, 50);
  if ([prevFast, prevSlow, fast, slow].some(value => value === null)) return { signal: "insufficient_data" };
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
    const equityBefore = markEquity(state);
    if (state.day !== day) { state.day = day; state.startingDayEquity = equityBefore; }
    if (state.emergencyStop || !state.enabled) {
      state.lastScan = { at: now.toISOString(), skipped: true, reason: state.emergencyStop ? "HIC-STOP-01 engaged" : "paper automation disabled" };
      await this.ctx.storage.put("state", state);
      return { ok: true, ...state.lastScan, mode: "paper-only" };
    }
    if (equityBefore <= state.startingDayEquity * (1 - DAILY_LOSS_LIMIT)) {
      state.enabled = false; state.emergencyStop = true;
      state.lastScan = { at: now.toISOString(), skipped: true, reason: "daily paper drawdown limit reached", drawdownLimit: DAILY_LOSS_LIMIT };
      await this.ctx.storage.put("state", state);
      return { ok: true, ...state.lastScan, control: "HIC-STOP-01", equity: markEquity(state) };
    }
    const signals = [];
    for (const symbol of SYMBOLS) {
      try {
        const response = await fetch(BINANCE + "?symbol=" + symbol + "&interval=1h&limit=100", { headers: { Accept: "application/json" } });
        if (!response.ok) throw new Error("market data HTTP " + response.status);
        const candles = await response.json();
        if (!Array.isArray(candles) || candles.length < 52) throw new Error("insufficient candle data");
        const closes = candles.slice(0, -1).map(candle => Number(candle[4]));
        const price = closes[closes.length - 1], signal = getSignal(closes).signal;
        if (!Number.isFinite(price) || price <= 0) throw new Error("invalid market price");
        state.lastPrices[symbol] = price;
        if (markEquity(state) <= state.startingDayEquity * (1 - DAILY_LOSS_LIMIT)) {
          state.enabled = false; state.emergencyStop = true;
          state.lastScan = { at: now.toISOString(), skipped: true, reason: "daily paper drawdown limit reached", drawdownLimit: DAILY_LOSS_LIMIT };
          state.updatedAt = now.toISOString(); await this.ctx.storage.put("state", state);
          return { ok: true, ...state.lastScan, control: "HIC-STOP-01", equity: markEquity(state) };
        }
        signals.push({ symbol, price, signal, at: now.toISOString() });
        if (signal === "buy" && !state.positions[symbol]) {
          const availableCash = Math.max(0, Number(state.cash) || 0);
          const notional = Math.min(availableCash / (1 + FEE_RATE), Math.max(0, markEquity(state) * MAX_POSITION_FRACTION));
          if (notional > 10) {
            const fee = notional * FEE_RATE; state.cash -= notional + fee;
            state.positions[symbol] = { quantity: notional / price, entryPrice: price, entryFee: fee, openedAt: now.toISOString() };
            state.trades.push({ side: "BUY", symbol, price, notional, fee, mode: "paper", at: now.toISOString() });
          }
        } else if (signal === "sell" && state.positions[symbol]) {
          const pos = state.positions[symbol], proceeds = pos.quantity * price, fee = proceeds * FEE_RATE;
          const pnl = proceeds - fee - pos.quantity * pos.entryPrice - pos.entryFee;
          state.cash += proceeds - fee; state.realizedPnl += pnl;
          state.trades.push({ side: "SELL", symbol, price, quantity: pos.quantity, fee, pnl, mode: "paper", at: now.toISOString() });
          delete state.positions[symbol];
        }
      } catch (error) {
        signals.push({ symbol, signal: "unavailable", error: String(error?.message || error).slice(0, 160), at: now.toISOString() });
      }
    }
    state.signals = signals; state.lastScan = { at: now.toISOString(), skipped: false, mode: "paper-only" };
    state.updatedAt = now.toISOString(); await this.ctx.storage.put("state", state);
    await this.ctx.storage.setAlarm(Date.now() + 15 * 60 * 1000);
    return { ok: true, mode: "paper-only", equity: markEquity(state), cash: state.cash, realizedPnl: state.realizedPnl, signals };
  }
}
function markEquity(state) {
  let equity = Number(state.cash || 0);
  for (const [symbol, position] of Object.entries(state.positions || {})) equity += position.quantity * Number(state.lastPrices?.[symbol] || position.entryPrice || 0);
  return equity;
}
function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}
