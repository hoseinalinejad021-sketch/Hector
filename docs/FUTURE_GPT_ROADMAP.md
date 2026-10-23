# Hector Future GPT — Research-Grounded Roadmap

## Purpose

Build Hector as a personal, reliable, tool-using assistant that can carry a goal through planning, bounded execution, verification, recovery, and transparent reporting. This roadmap is a product/engineering direction, not a claim that artificial general intelligence, unlimited self-improvement, or flawless autonomy exists today.

## Research baseline

Modern agent architectures increasingly combine a model with tool calling, durable task state, context compaction, reusable skills, sandboxed execution, and evaluations. OpenAI's public agent documentation describes these as separate runtime capabilities rather than abilities that appear from a prompt alone:
- Agents overview: https://developers.openai.com/api/docs/guides/agents
- Tool use: https://developers.openai.com/api/docs/guides/tools
- Long-running task memory and compaction example: https://developers.openai.com/cookbook/examples/agents_sdk/building_reliable_agents_memory_compaction
- Computer-use tool: https://developers.openai.com/api/docs/guides/agents-api/tools/computer-use
- International AI Safety Report 2026 discusses increasing failure risk on longer tasks and the need for evaluations and oversight: https://internationalaisafetyreport.org/sites/default/files/2026-02/international-ai-safety-report-2026_1.pdf

These sources support an incremental architecture. They do not imply that Hector has access to hosted agent runtimes, computer control, or multi-agent orchestration unless those integrations are explicitly configured and tested.

## Target architecture

1. **Model gateway**
   - Keep model/provider configuration separate from task orchestration.
   - Support Responses API tool calls and retain the existing compatible chat path.
   - Validate HTTPS endpoints, response IDs, output size, timeouts, and errors.
   - Never embed API keys in source, memory, logs, tool output, or cloud sync.

2. **Task controller**
   - Convert the user's goal into a bounded plan with unique step IDs, tool names, arguments, risk classification, timeout, retry budget, and expected postconditions.
   - Reject malformed plans before execution.
   - Limit concurrent work and total steps; prevent infinite loops.
   - Keep planning separate from permission to execute.

3. **Capability registry**
   - Expose only explicitly registered tools with schemas and least-privilege access.
   - Load tool descriptions when needed rather than granting arbitrary access.
   - Start with read-only memory/history; add file inspection and diagnostics only with explicit path boundaries.
   - Do not expose an unrestricted shell, root access, arbitrary network access, or hidden actions to the model.

4. **Evidence-based verifier**
   - Distinguish a model's claim from observed execution.
   - Require postconditions that can be independently checked.
   - Record success only when all required checks pass; otherwise report incomplete, failed, or cancelled.
   - Preserve concise error categories and evidence references for debugging.

5. **Durable memory and task recovery**
   - Separate user-approved facts, conversation history, task journal, and reusable workflow lessons.
   - Apply retention limits, secret redaction, schema versioning, and corruption recovery.
   - Compact long context into bounded summaries while preserving open tasks, constraints, decisions, and evidence.
   - Never treat retrieved memory or tool output as trusted instructions.
   - Resume only idempotent or explicitly revalidated steps; do not blindly replay external side effects.

6. **Approval and autonomy levels**
   - Level 0: answer only.
   - Level 1: read-only tools.
   - Level 2: reversible app-local changes, with visible diff and undo strategy.
   - Level 3: external, destructive, financial, communication, installation/update, or privileged actions; require explicit, scoped user approval.
   - No model output can grant itself additional permissions. Approval is bound to a specific action and expires when its arguments change.

7. **Safe repair loop**
   - Observe a failure, classify it, form a bounded repair proposal, apply it only in an isolated workspace, run relevant tests, inspect the diff, and report evidence.
   - Enforce a retry ceiling and stop on repeated failures or security-boundary violations.
   - Changes to production branches, signing keys, secrets, release workflows, or user data require explicit authorization.
   - Never claim that a build or repair succeeded without the actual test/build result.

8. **Optional specialist agents**
   - Add planner, implementer, reviewer, and test analyst as isolated roles only after single-agent orchestration is stable.
   - Give each specialist a narrow task and independent output limits.
   - Require the controller to reconcile disagreements and verify results; agent consensus is not proof.
   - Do not run multiple agents just for appearance or when it adds cost without measurable benefit.

9. **Multimodal and device capabilities**
   - Add image/audio/document inputs through explicit, permission-aware Android APIs.
   - Background voice must obey Android foreground-service and notification rules and provide a visible stop control.
   - Computer/UI interaction must use a supported runtime and explicit scope; do not claim unrestricted control of the phone.
   - Preserve FLAG_SECURE, HIC-STOP-01, and the user's no-biometrics requirement.

10. **Evaluation and observability**
    - Maintain unit tests for parser boundaries, policy decisions, cancellation, redaction, malformed persistence, and verification.
    - Add scenario tests for prompt injection in retrieved content, repeated tool calls, timeout, offline mode, partial failure, and resume.
    - Report success rate, recovery rate, retries, unsafe-action blocks, latency, and cost; do not rely on a single aggregate score.
    - Keep logs privacy-preserving and redact secrets before persistence.

## Delivery sequence

### Phase A — reliability foundation (current branch)
- Make parser tool names match the exact runtime allowlist.
- Make the JVM test suite deterministic and obtain a green CI run.
- Wire the task journal and verifier into actual task execution, with truthful states.
- Add tests for persistence, cancellation, and output redaction.
- Keep the current model-callable tools read-only.

### Phase B — visible task lifecycle
- Show plan and step states.
- Add pause/cancel and bounded retries.
- Persist task checkpoints and recover safely after process death.
- Add explicit approval UI for future risky tools.

### Phase C — constrained workspace tools
- Add read-only project/file inspection first.
- Add patch proposals in a workspace and show diffs before applying.
- Run tests in a sandbox and verify the result.
- Do not give the agent access to signing secrets or unrestricted shell execution.

### Phase D — cloud and multi-device continuity
- Sync only opted-in, minimum-necessary task metadata and memory.
- Use authenticated, user-scoped database rules and conflict handling.
- Keep API credentials and local-only secrets out of sync.
- Make offline behavior explicit.

### Phase E — optional specialist orchestration and multimodality
- Prototype only after evaluation gates pass.
- Compare against the single-agent baseline for quality, cost, latency, and failure modes.
- Enable each capability independently and make permissions visible.

## Release gates

A phase is not complete because its code or documentation exists. It is complete only when:
- CI is green on the exact branch head;
- tests cover both expected behavior and rejection paths;
- security invariants remain intact;
- runtime limitations are documented;
- observed results support any success claim;
- release actions receive separate authorization.

## Current limitations

Hector does not self-train its underlying foundation model. It can improve its workflow through reviewed code changes, versioned instructions, retrieval of approved lessons, and test-driven repair proposals. Long-term autonomous research, general intelligence, flawless self-correction, and safe unrestricted self-improvement remain unsolved and are not claimed by this project.
