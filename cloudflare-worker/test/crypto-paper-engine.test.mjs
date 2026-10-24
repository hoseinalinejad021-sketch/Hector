import test from "node:test";
import assert from "node:assert/strict";
import { ema, getSignal } from "../src/crypto-paper-engine.js";
test("EMA rejects insufficient data", () => assert.equal(ema([1, 2], 3), null));
test("EMA returns a finite average", () => assert.ok(Number.isFinite(ema(Array.from({length: 60}, (_, i) => i + 1), 20))));
test("signal rejects insufficient or invalid data", () => {
  assert.equal(getSignal([1, 2, 3]).signal, "insufficient_data");
  assert.equal(getSignal(Array(60).fill(0)).signal, "insufficient_data");
});
test("flat price series returns hold", () => assert.equal(getSignal(Array(100).fill(100)).signal, "hold"));
