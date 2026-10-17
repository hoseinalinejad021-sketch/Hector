# Hector security gate and release checklist

## Implemented prototype flow
1. Android strong biometric prompt (BIOMETRIC_STRONG) is required before the voice action is enabled. Hector does not receive Android biometric templates.
2. The biometric prompt is bound to an Android Keystore AES-GCM operation with a fresh random challenge.
3. After the biometric stage, the user can explicitly grant microphone permission and create a local owner voice profile from three separate three-second samples, or compare a new three-second sample against the saved profile.
4. The speaker embedding is computed locally by sherpa-onnx. Raw PCM samples are held temporarily in memory and cleared after inference. Only a normalized speaker-embedding centroid is persisted using SecureMemoryStore (AES-GCM with an Android Keystore key).
5. A matching voice embedding is informational only and does NOT unlock sensitive features or network access.

## Important security limitations
- This is a functional voice-matching prototype, not production-grade owner authentication.
- The cosine threshold is an initial placeholder and has not been calibrated using false-accept/false-reject testing on this phone or model.
- Dynamic spoken-challenge recognition, anti-replay detection, and validated voice liveness detection are NOT implemented. A recording or synthetic voice may potentially be accepted by a speaker embedding model.
- Consequently, the application deliberately keeps sensitive operations and network features locked even when a voice match succeeds. Do not describe this version as fully authenticated or liveness-protected.
- A successful CI build only proves that the source compiled and an artifact was produced; it does not prove installation, runtime behavior, voice accuracy, or device security.

## Fail-closed rules
- Biometric cancellation/failure, missing model, microphone denial, recording errors, malformed profile, embedding errors, or process restart must not unlock sensitive actions.
- HIC-STOP-01 keeps sensitive actions locked. The user controls the stop action; the app must not change Android security settings.
- No unrelated audio-file access: voice samples are recorded only after an explicit tap and microphone permission.
- Raw PCM is not written to disk or sent over the network. Only the encrypted embedding profile is stored in app-private preferences.
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
- Full authorization state machine that unlocks protected actions (intentionally not enabled).
- Physical-device testing: install/upgrade, model loading, enrollment, successful and failed voice comparison, permission denial, restart persistence, biometric failure/cancellation, and stop behavior.
- Recovery behavior after app data clearing, Keystore invalidation, or corrupted profile.


## Anti-replay challenge component (prototype, not yet active in the app)
- Added `ReplayChallenge.kt`: generates a fresh random three-word phrase, expires it after 15 seconds, and consumes it on the first verification attempt (success or failure). It rejects expired challenges, low/invalid recognizer confidence, and transcript mismatches.
- This component is deliberately not described as a complete anti-spoofing solution. It is not yet wired into the UI or an on-device speech recognizer. Integration must ensure the recognized phrase and speaker embedding come from the SAME microphone capture; two separate recordings would permit a split-replay bypass.
- The caller must use a trusted on-device recognizer only. If no on-device recognizer or reliable confidence score is available, the check must fail closed. A random spoken phrase can raise the bar against pre-recorded replay, but it does not stop real-time voice synthesis or prove biological liveness.
- Sensitive actions and network access remain locked. This change does not grant voice-based authorization and does not establish final security approval.

## Same-capture spoken challenge integration (implemented in source; CI/device validation pending)
- Hector now generates a fresh random three-word English phrase with a 15-second expiry before each enrollment or verification recording.
- One five-second mono 16 kHz PCM capture is passed unchanged to both the local sherpa-onnx Whisper tiny.en ASR engine and the speaker-embedding engine. The recognized transcript must match the active one-use challenge before enrollment or speaker comparison is accepted.
- The challenge is consumed on the first attempt and cleared on errors/restart. If either local model fails to load, the voice action remains disabled. Raw PCM is cleared from the app's temporary arrays after processing.
- CI downloads the official sherpa-onnx Whisper tiny.en int8 encoder/decoder and token files for packaging. The ASR archive's SHA-256 is logged for traceability but is not yet pinned to an independently verified expected hash; model provenance pinning remains a release task.
- This is a meaningful replay-resistance layer, not proof of biological liveness. It may reduce acceptance of fixed recordings but cannot guarantee resistance to real-time voice cloning, replay with prompt injection, ASR errors, or adversarial audio. ASR confidence is not exposed by this integration; matching requires exact normalized transcript text.
- A voice match remains informational only. Sensitive actions and network access remain locked. Final security approval requires successful CI, physical-device tests, threshold calibration and spoof/adversarial evaluation.
