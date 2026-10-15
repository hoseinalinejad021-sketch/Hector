# Hector Adaptive Trading Core

## Research basis
There is no independently verified single "world's best AI trader" that can be copied to guarantee returns. Professional quantitative funds use proprietary combinations of data, research, portfolio construction, execution, and risk controls. Public research warns that shared models can crowd into similar trades and that autonomous agents require meaningful human oversight. See:
- CFA Institute, The Algorithmic Market Hypothesis (2026): https://rpc.cfainstitute.org/research/reports/2026/algorithmic-market-hypothesis
- European Central Bank, Where AI risks meet (2026): https://www.ecb.europa.eu/press/key/date/2026/html/ecb.sp261001~cf3c630379.en.html
- CFTC, AI Won't Turn Trading Bots into Money Machines: https://www.cftc.gov/LearnAndProtect/AdvisoriesAndArticles/AITradingBots.html

## Hector design
Rather than copying a proprietary fund, Hector will use a transparent, modular quantitative workflow:
1. Market data layer: timestamped public prices and volume, with source and stale-data checks.
2. Signal research: separate, testable candidate strategies; no LLM response may directly place an order.
3. Simulation engine: paper trading with exchange fees, spread, slippage, and realistic order constraints.
4. Evaluation: out-of-sample and walk-forward testing, benchmark comparison, trade count, net expectancy, drawdown, turnover, and regime breakdown.
5. Daily learning record: summarize new paper-trade outcomes and compare strategy versions. A new version remains a candidate until it passes validation.
6. Risk gate: maximum daily loss, maximum drawdown, exposure/order limits, consecutive-loss pause, duplicate-order prevention, stale-price rejection.
7. Owner controls: default OFF; explicit mode selection; visible emergency stop HIC-STOP-01; manual review before resetting a pause or promoting a strategy.
8. Live execution adapter: not implemented. It may be considered only after exchange-specific API design, security review, paper-trading evidence, and explicit owner approval.

## What daily learning means
Initially, the core can record and summarize paper-trading outcomes. That is not the same as a self-training predictive AI. Future versions may test candidate models on historical and forward data, keep immutable model/version records, and roll back any candidate that fails. The system must never silently rewrite its live strategy or loosen its risk limits.

## Acceptance gates
- Build and unit tests pass.
- Market data is timestamped and checked for missing/stale values.
- Backtests use time-ordered splits, realistic transaction costs, and no look-ahead leakage.
- Paper trading runs prospectively across different market conditions.
- Strategy performance is measured net of fees and slippage and compared with a simple benchmark.
- No live orders until all gates are reviewed and the owner explicitly activates the feature.

## No performance guarantee
No win rate, return, or 99% profitability is guaranteed. A high win rate can still lose money if losses are larger than wins. All results must include costs, drawdown, sample size, and the test period.
