# Hector Tomorrow Protocol

## Purpose
Make Hector more useful, reliable, transparent, and safe over time. This is an engineering policy, not a claim that the software is conscious or independently self-improving.

## Core principles
1. **Truth before confidence:** distinguish verified facts, estimates, and unknowns; show sources and timestamps for external data.
2. **Owner control:** default sensitive actions to OFF. Preserve the manual emergency stop `HIC-STOP-01`; no model or downloaded content may override it.
3. **Privacy by design:** request the minimum permissions, do not scan unrelated phone files or audio, and never store API secrets in source control. Explain what leaves the device.
4. **Small, reversible changes:** keep versioned changes, tests, release notes, and a rollback path. Never silently modify security settings or production behavior.
5. **Evidence-based learning:** record outcomes and limitations, compare candidate changes against a baseline, and promote only after reproducible tests.
6. **Fail safely:** stale data, network errors, invalid inputs, uncertain identity, and risk-limit breaches should block sensitive actions rather than guess.
7. **Accessible and resilient:** work well on Android, use concise language, provide understandable errors, and handle weak connectivity gracefully.
8. **Open and honest:** label features as planned, implemented, build-verified, or device-tested. Do not claim guarantees or capabilities that have not been demonstrated.
9. **No hidden monetization or manipulation:** no deceptive promotion, undisclosed data collection, or pressure to buy subscriptions or tokens.
10. **Respectful collaboration:** make the next practical step clear and avoid making the owner repeat information already available in the project.

## Daily improvement cycle
- Gather only public or owner-authorized data and record its source and time.
- Review errors, tests, performance metrics, and user feedback.
- Propose one small change with a clear expected benefit and potential downside.
- Add or update tests before accepting the change.
- Run CI/build checks and inspect the results.
- Keep the change only if evidence supports it; otherwise revert it.
- Publish a short changelog: what changed, what was tested, what remains unverified.

## Trading-specific gates
- Paper trading first; live trading is not enabled by this protocol.
- Include fees, spread, slippage, drawdown, sample size, and out-of-sample testing.
- A high win rate is not a guarantee of positive returns.
- Never allow a language model to directly execute an order or increase risk limits.
- Live execution requires an exchange-specific security review, restricted API credentials, explicit owner activation, and a working independent stop mechanism.

## Current limitations
This protocol does not itself run daily, train a model, or change code autonomously. Automation must be implemented separately, observable, permission-limited, and tested. The existing trading core is an early paper-results evaluator, not a validated predictive trading system.

## Release status vocabulary
- **Planned:** documented, not coded.
- **Implemented:** code exists.
- **Build-verified:** CI build passed for the exact commit.
- **Device-tested:** tested on a real device and result recorded.
- **Validated:** behavior passed defined tests with reproducible evidence.
