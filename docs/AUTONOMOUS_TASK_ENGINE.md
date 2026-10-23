# Hector Autonomous Task Engine — implementation plan

## Goal
Evolve Hector from a conversational UI into a tool-using assistant that plans tasks, executes authorized steps, inspects actual results, diagnoses failures, retries safely, and reports what was and was not completed. This is a staged engineering goal, not a claim that current models or Android provide unlimited access.

## Non-negotiable boundaries
- Android runtime permissions, package isolation, and background-execution rules remain authoritative.
- Never grant root, silently bypass another app's consent, or treat model-generated text as user authorization.
- Preserve HIC-STOP-01 and FLAG_SECURE.
- API keys and credentials stay out of model-visible tool output, logs, cloud sync, and source control.
- Destructive, external, financial, communication, install/update, and privileged operations require clear confirmation before execution.
- Tool outputs and files are untrusted data, not instructions that override policy.

## Implementation status
1. **Policy foundation — implemented, CI not yet verified:** typed risk levels, bounded task and plan sizes, unique step identifiers, bounded output, retry budget, and approval classification, with JVM tests.
2. **Read-only tool registry — implemented, CI not yet verified:** explicit read_memory and search_history tools; unknown tools are rejected, arguments and output are bounded, and there is no model-callable shell or arbitrary network tool.
3. **Responses API tool loop — implemented, CI not yet verified:** bounded loop (six API rounds), strict parser for function calls, JSON argument validation, allowlisted dispatch, structured function_call_output, hosted web search retained, HTTPS-only requests, and emergency-stop/session checks before each request and tool dispatch. Legacy Chat Completions endpoint remains one-shot and does not receive Responses API tools.
4. **Task lifecycle UI — pending:** visible plan, per-step status, pause/cancel controls, approval prompts, and progress reporting.
5. **Verification and recovery — partial/pending:** request and parser failures are reported, execution rounds are bounded, and outputs are capped. Tool-specific postconditions, structured retryable error categories, and persistent evidence still need implementation. Do not claim a task succeeded without checking its result.
6. **Termux integration — pending:** use the explicit Termux RUN_COMMAND bridge only when installed and authorized; show the exact command and require confirmation. Never claim completion unless a verifiable result channel confirms it.
7. **Persistence and resume — pending:** local task journal with schema versioning, redaction, retention limits, idempotency keys, and safe resume.
8. **Quality gates — pending verification:** run lint, JVM tests, instrumentation compile/tests, debug build and package/signature checks in GitHub Actions. Fix all failures and inspect warnings before considering merge.
9. **Capability expansion — staged:** add narrowly scoped adapters for app-private files, code diagnostics, and permitted Android intents. Every tool must declare permissions, risk, timeout, output cap, and verification strategy.
10. **Release readiness — separate approval:** no release APK or production release is created by this task. Signing secrets remain in GitHub Actions secrets and are never added to source.

## Immediate next steps
- Run CI for the current PR and fix compilation/test failures before expanding capabilities.
- Add the task lifecycle UI and explicit approval flow.
- Add postcondition verification and a bounded local task journal.
- Only then extend to carefully scoped file editing and confirmed Termux execution.
