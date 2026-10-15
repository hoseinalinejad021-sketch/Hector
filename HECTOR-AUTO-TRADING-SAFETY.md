# Hector Automated Trading — Safe Implementation Plan

## Status
Design only. Live exchange trading is NOT implemented or enabled. The current APK only displays public market data and does not place orders.

## Owner-controlled activation
- Default state: OFF after every install/reset; no order may be placed on startup.
- Separate modes: Paper trading (simulation) and Live trading.
- Live mode requires the owner to select a supported exchange, review the exact permissions and risk limits, and explicitly confirm activation in the app.
- A persistent, clearly visible STOP control must cancel pending bot orders where the exchange supports cancellation and prevent new orders locally. It must not be possible for the bot/model to disable or override this control.
- No AI-generated text can directly execute an order. A deterministic strategy/risk layer must validate every proposed order.

## Exchange and credential requirements
- Each exchange needs its own documented API integration; merely having its app installed on the phone does not grant Hector access to it.
- Use official exchange APIs only. Never ask for exchange passwords, seed phrases, or withdrawal permissions.
- API keys must be entered by the owner, stored only using Android Keystore-backed encryption if persistence is later added, never committed to GitHub, and be revocable from the exchange.
- Start with read-only market/account access. Live trading must use a separate key with trading permission only, withdrawals disabled, and IP restrictions where supported.
- Do not embed exchange keys or a shared developer key in the APK.

## Required risk controls before live trading
- Spot-only initially; no leverage, margin, futures, or borrowing.
- Owner-set maximum order size, maximum total exposure, maximum daily loss, and maximum number of trades.
- Reject stale prices, invalid symbols, excessive spread/slippage, duplicate orders, and orders outside exchange minimum/maximum rules.
- Stop opening new positions after the daily loss limit; provide a kill switch and clear order/error logs.
- Display fees and estimated slippage; do not describe historical performance as a guarantee.

## Validation gates
1. Compile and unit-test order validation.
2. Backtest with realistic fees and slippage, avoiding look-ahead bias.
3. Run paper trading prospectively without real funds.
4. Review results across different market conditions.
5. Only then consider a small owner-authorized live test, after exchange-specific implementation and security review.

## Profitability statement
No strategy can credibly promise 99% profitable trades or 99% returns. Win rate alone is not profitability: fees, losses per trade, slippage, and drawdowns matter. Any performance claims must be supported by transparent, reproducible results and must not imply guaranteed returns.
