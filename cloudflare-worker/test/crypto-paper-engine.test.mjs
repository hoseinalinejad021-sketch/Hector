import test from "node:test";
import assert from "node:assert/strict";
import { ema, getSignal, HectorCryptoPaperEngine } from "../src/crypto-paper-engine.js";
test("EMA rejects insufficient data", () => assert.equal(ema([1, 2], 3), null));
test("EMA returns a finite average", () => assert.ok(Number.isFinite(ema(Array.from({length: 60}, (_, i) => i + 1), 20))));
test("signal rejects insufficient or invalid data", () => {
  assert.equal(getSignal([1, 2, 3]).signal, "insufficient_data");
  assert.equal(getSignal(Array(60).fill(0)).signal, "insufficient_data");
});
test("flat price series returns hold", () => assert.equal(getSignal(Array(100).fill(100)).signal, "hold"));

test("fresh market marks trigger HIC-STOP-01 before any paper trade", async () => {
  const state = {
    mode: "paper-only", enabled: true, emergencyStop: false, cash: 9000,
    positions: { BTCUSDT: { quantity: 10, entryPrice: 100, entryFee: 0, openedAt: "2026-10-24T00:00:00.000Z" } },
    lastPrices: { BTCUSDT: 100 }, trades: [], signals: [],
    startingDayEquity: 10000, day: new Date().toISOString().slice(0, 10), realizedPnl: 0
  };
  const storage = {
    async get(key) { return key === "state" ? structuredClone(state) : undefined; },
    async put(key, value) { if (key === "state") Object.assign(state, structuredClone(value)); },
    async setAlarm() {}
  };
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify(
    Array.from({ length: 100 }, () => [0, "70", "70", "70", "70"])
  ), { status: 200, headers: { "Content-Type": "application/json" } });
  try {
    const engine = new HectorCryptoPaperEngine({ storage }, {});
    const result = await engine.scan();
    assert.equal(result.control, "HIC-STOP-01");
    assert.equal(state.enabled, false);
    assert.equal(state.emergencyStop, true);
    assert.equal(state.trades.length, 0);
    assert.equal(result.equity, 9700);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
