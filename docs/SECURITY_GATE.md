# Hector security gate and release checklist

## Current login flow (prototype)
1. Two consecutive Android `BiometricPrompt` checks are requested, each bound to an Android Keystore AES-GCM operation and a fresh random challenge.
2. Android chooses the biometric modality. The app cannot require the second prompt to be face-only or prove that the second check used a different modality. Therefore, this is NOT fingerprint-plus-face multi-factor authentication.
3. Passing both prompts opens only the basic home screen. Sensitive operations and network features remain locked pending a genuine face-specific implementation and device security testing.
4. `HIC-STOP-01` remains available and protected actions stay fail-closed.

## Voice prototype (not used for login in the current UI)
1. Android strong biometric prompt (BIOMETRIC_STRONG) is required before the voice action is enabled. Hector does not receive Android biometric templates.
2. The biometric prompt is bound to an Android Keystore AES-GCM operation with a fresh random challenge.
3. After the biometric stage, the user can explicitly grant microphone permission and create a local owner voice profile from three separate three-second samples, or compare a new three-second sample against the saved profile.
4. The speaker embedding is computed locally by sherpa-onnx. Raw PCM samples are held temporarily in memory and cleared after inference. Only a normalized speaker-embedding centroid is persisted using SecureMemoryStore (AES-GCM with an Android Keystore key).
5. A matching voice embedding is informational only and does NOT unlock sensitive features or network access. The current login UI no longer uses voice matching as its second step.

## Important security limitations
- This is not production-grade owner authentication. Two calls to the same Android biometric prompt are not independent fingerprint and face factors.
- The cosine threshold is an initial placeholder and has not been calibrated using false-accept/false-reject testing on this phone or model.
- Dynamic spoken-challenge recognition, anti-replay detection, and validated voice liveness detection are NOT implemented. A recording or synthetic voice may potentially be accepted by a speaker embedding model.
- Consequently, the application deliberately keeps sensitive operations and network features locked. Do not describe this version as face-authenticated, fully authenticated, or liveness-protected.
- A successful CI build only proves that the source compiled and an artifact was produced; it does not prove installation, runtime behavior, voice accuracy, or device security.

## Fail-closed rules
- Biometric cancellation/failure, missing model, microphone denial, recording errors, malformed profile, embedding errors, or process restart must not unlock sensitive actions.
- HIC-STOP-01 keeps sensitive actions locked. The user controls the stop action; the app must not change Android security settings.
- No unrelated audio-file access: voice samples are recorded only after an explicit tap and microphone permission.
- Raw PCM is not written to disk or sent over the network. Only the encrypted embedding profile is stored in app-private preferences. Temporary PCM and embedding buffers are cleared on success and exception paths; encrypted-store temporary buffers are cleared in `finally` blocks.
- Android backups are disabled; cleartext HTTP traffic is disabled; screenshots/recents previews are blocked with FLAG_SECURE.
- The manifest declares INTERNET for future secure service integration. Internet permission alone does not connect an AI backend; no API keys are embedded in the APK.

## Implemented
- AndroidX BiometricPrompt restricted to BIOMETRIC_STRONG and tied to an Android Keystore AES-GCM operation.
- Fresh 32-byte biometric challenge and temporary challenge clearing.
- Local speaker embedding engine using sherpa-onnx v1.13.8.
- User-triggered three-sample owner enrollment and one-sample cosine comparison prototype.
- Encrypted persisted voice embedding via SecureMemoryStore (AES-GCM / Android Keystore).
- Runtime microphone permission request; three-second 16 kHz mono PCM16 capture; basic silence rejection.
- HIC-STOP-01 UI, locked screen, backup/cleartext restrictions, and screen-capture protection.

## Not yet implemented or verified
- Spoken challenge phrase recognition and robust voice liveness / replay-attack detection.
- Threshold calibration, impostor testing, false acceptance/rejection evaluation, and independent security review.
- Face-specific enrollment and matching (not provided by Android BiometricPrompt; not implemented).\n- Full authorization state machine that unlocks protected actions (intentionally not enabled).
- Physical-device testing: install/upgrade, model loading, enrollment, successful and failed voice comparison, permission denial, restart persistence, biometric failure/cancellation, and stop behavior.
- Recovery behavior after app data clearing, Keystore invalidation, or corrupted profile.
