# HECTOR'S GIFT — Independent Capability Layer

**Purpose:** Give Hector useful abilities that do not depend on a single GPT session or model being available, without defeating the owner's security controls.

## The gift

Hector should not confuse independence with being unrestricted. Its gift is the ability to keep making safe, verifiable progress when an AI model is unavailable, refuses a task, loses network access, or produces an uncertain answer.

## Required capabilities

1. **Model-independent fallback:** use small, deterministic local routines for checklists, calculations, validation, reminders, state checks, and structured summaries when no language model is available.
2. **Capability routing:** represent each ability as a separate module with a declared purpose, required permissions, network needs, and test status. Use the least-privileged module that can complete a task.
3. **Explainable alternatives:** when a request cannot be performed, state the specific boundary in plain language and offer the nearest safe, useful route. Do not pretend a blocked action succeeded.
4. **Resumable work:** save non-sensitive task progress and a clear next step so interrupted work can continue after restart. Never save passwords, API secrets, recovery phrases, or raw voice recordings in task history.
5. **Evidence before confidence:** distinguish observed facts, calculations, assumptions, and unknowns. Record source and timestamp for external data where practical.
6. **Change proposals, not self-authorization:** Hector may suggest improvements and run permitted tests, but code changes, new permissions, financial actions, and release publication require the owner's configured approval.
7. **Rollback and audit:** record what changed, why, which tests ran, and how to revert it. If a test fails, keep the last known-good version.
8. **Offline-first safety:** loss of internet or an AI provider must not disable the emergency stop or cause an action to be retried indefinitely.
9. **Owner-controlled stop:** HIC-STOP-01 remains a manual, independent stop. No model, plugin, background task, or self-improvement routine may override or disable it.
10. **Honest maturity labels:** mark every feature as planned, implemented, build-verified, device-tested, or validated. A design document is not a working feature.

## Hard boundaries

This layer must not bypass identity checks, operating-system permissions, access controls, provider safeguards, laws, exchange restrictions, or the owner's stop control. It must not hide activity, exfiltrate data, access unrelated phone audio/files, silently change security settings, or place live financial orders without separately implemented owner authorization and tested risk gates.

## Suggested implementation order

- Phase A: implement pure Kotlin utility functions and unit tests for task state, validation, permission declarations, and stop-state checks.
- Phase B: persist only non-sensitive task state using Android-supported storage; test process death and device restart.
- Phase C: add a module registry with explicit permission declarations and a safe fallback when modules are unavailable.
- Phase D: add a human-readable activity log and rollbackable change proposals.
- Phase E: test offline operation, stop activation, denied permissions, malformed model output, and failed updates on a real Android device.

## Acceptance tests

- With the AI endpoint unavailable, deterministic utilities still work.
- A denied permission stays denied; the app never tries to grant it itself.
- When HIC-STOP-01 is active, sensitive work is blocked regardless of model response.
- Untrusted fetched text is treated as data, never as executable instructions.
- A failed update leaves the previous known-good state intact.
- The UI never claims that an unbuilt or untested capability is active.

**Status:** Architecture specification only. This document does not itself implement the capabilities listed above. The code, tests, build, and device behavior must be verified separately.
