# Hector security gate and release checklist

## Current prototype behavior
1. Android strong biometric verification is required before the voice action is enabled. Hector never receives Android biometric templates.
2. The biometric prompt is bound to an Android Keystore AES-GCM operation with a fresh random challenge.
3. After that step, the user can explicitly grant microphone access and enroll three owner-voice samples, or compare a new sample against the saved profile.
4. The speaker embedding model and speech recognition both run locally. A five-second, mono 16 kHz PCM capture is passed to both the sherpa-onnx multilingual Whisper tiny recognizer configured for Persian (fa) and the speaker-embedding engine. The transcript must exactly match a fresh three-word Persian challenge before enrollment or comparison continues.
5. Persian transcript normalization handles common Arabic/Persian character variants, diacritics, and extra whitespace. It does not attempt fuzzy matching, because that could accept a different phrase.
6. Only the encrypted speaker-embedding profile is persisted using AES-GCM with an Android Keystore key. Temporary PCM and temporary embeddings are cleared after processing.
7. A matching voice profile is informational only. It does NOT unlock sensitive actions or network access.

## Fail-closed controls
- If biometrics fail/cancel, microphone access is denied, either model fails to load, the challenge mismatches/expires, recording fails, or profile processing fails, no sensitive access is granted.
- HIC-STOP-01 remains user-controlled. The app must not alter its own control settings or Android security settings.
- Audio is recorded only after an explicit user action and microphone permission. Hector must not browse unrelated audio files.
- Raw PCM is not written to disk or sent over the network.
- Android backups and cleartext traffic are disabled; screen-capture/recents previews are blocked.
- INTERNET permission alone does not connect an AI backend; no API keys are embedded in the APK.
- Sensitive actions and network access remain locked until real-device testing and independent security review are complete.

## Known limitations
- This is a prototype, not production-grade owner authentication.
- The multilingual Whisper tiny model supports multiple languages, including Persian, but recognition accuracy for this exact three-word challenge has not yet been tested on the target phone. Exact transcript matching can still reject correctly spoken phrases because of speech-recognition errors.
- Exact transcript matching is a replay-resistance layer, not biological liveness. It cannot guarantee resistance to real-time voice cloning, adversarial audio, or ASR errors.
- ASR confidence is not exposed by this integration; matching requires exact normalized transcript text.
- The speaker-similarity threshold is a placeholder and has not been calibrated with false-accept/false-reject testing.
- The model archive SHA-256 is logged in CI but is not pinned to an independently verified expected value.
- The APK includes a large offline ASR model. The build excludes bundled desktop sherpa runtime files that are not needed on Android.

## Verification status
- The Persian build is a proposed change on the feature branch. GitHub Actions must complete successfully before a new APK artifact is ready.
- A successful build proves compilation and artifact creation only; it does not prove installation, runtime behavior, voice accuracy, or security.
- No physical-device test has been performed by the assistant.
- Before release, test installation/upgrade, model loading, enrollment, successful and failed owner-voice comparison, microphone denial, biometric cancellation/failure, restart persistence, HIC-STOP-01, and false-accept/replay scenarios on the target phone.
- Do not describe voice as a fully validated unlock mechanism until real-device testing and independent security review are complete.
