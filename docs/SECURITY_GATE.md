# Hector security gate plan

## Required unlock order
1. Android strong biometric prompt (the OS performs the biometric match; Hector never accesses fingerprint templates).
2. Owner-specific voice verification with live challenge and replay/liveness checks.
3. Only after both stages pass may protected network features be enabled.

## Fail-closed rules
- Any cancellation, mismatch, missing hardware, timeout, process restart, or app leaving foreground returns to locked state.
- HIC-STOP-01 forces the network/protected features to remain locked.
- No unrelated audio-file access. Microphone use, if later added, must be user-triggered in the foreground and permission-gated.
- Do not implement voice identity by matching a spoken phrase or speech-to-text; that is not speaker verification and can be replayed.
- Until a tested speaker-verification engine is integrated, the second gate must remain locked. No network capability may be advertised as unlocked.
- Security settings must not be changed automatically by the app.

## Current implementation boundary
The Android biometric prompt and a locked two-stage UI are implemented. Owner-specific speaker verification, liveness/replay detection, encrypted memory, and device-level integration tests remain pending. The UI intentionally refuses to unlock the network until those components are implemented and tested.
