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

## Stages
1. **Policy foundation (this change):** typed risk levels, bounded task and plan sizes, unique step identifiers, bounded output, retry budget, and approval classification, with JVM tests.
2. **Tool registry:** typed schemas and strict argument validation; every tool has a capability declaration, timeout, output cap, and audit event. Start with read-only workspace, memory search, app status, and file operations inside app-private directories.
3. **Responses API tool loop:** parse function-call items, validate calls against the registry, execute only approved tools, submit structured tool outputs, and continue until a final answer or a hard step/time budget. Never interpret arbitrary model text as a command.
4. **Task lifecycle UI:** visible plan, per-step status, cancel/pause, approval prompts, progress, and truthful final report. Emergency stop cancels network requests and prevents new tool dispatch.
5. **Verification and recovery:** tool-specific postconditions, structured error categories, at most two bounded retries for retryable failures, no retries for denied permissions or unsafe requests, and evidence of actual results.
6. **Termux integration:** use the explicit Termux RUN_COMMAND bridge only when installed and authorized; show the exact command and require confirmation. Never claim completion unless a verifiable result channel confirms it.
7. **Persistence and resume:** local task journal with schema versioning, redaction, retention limits, idempotency keys, and safe recovery after process death. Never persist API secrets.
8. **Quality gates:** unit tests for parser/policy/injection, instrumented permission/cancellation tests, mock API contract tests, dependency/security review, CI lint/test/debug-package checks, and manual device acceptance.
9. **Capability expansion:** add integrations one at a time through least-privilege adapters; document permissions, data sent off-device, rollback, and failure modes. A UI alone does not mean a feature is complete.
10. **Release readiness (separate approval):** privacy/legal identity and disclosures, Firebase rules and deletion checks, update flow, signing-secret verification, accessibility, battery/network behavior, and final checklist. This plan does not trigger a release build.

## Current known gap
The chat request currently reads only message text from Responses API output and ignores function-call items. Therefore these policy primitives are groundwork only; stages 2–5 are required before claiming autonomous tool execution. This pull request must not be described as a finished autonomous agent.
