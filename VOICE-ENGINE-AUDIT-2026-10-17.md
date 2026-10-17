# Hector Voice Engine Audit — 2026-10-17

## Scope
Reviewed the current `@Chat-GPT` branch files:
- `app/src/main/java/com/hector/android/SpeakerModelEngine.kt`
- `app/src/main/java/com/hector/android/VoiceIdentityFlow.kt`
- `app/src/main/java/com/hector/android/MainActivity.kt`
- `app/src/main/java/com/hector/android/SecureMemoryStore.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

## Findings
1. `MainActivity` does attempt to initialize `SpeakerModelEngine` asynchronously and displays a generic loaded/failed message.
2. The engine expects the asset `3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx` via Android assets.
3. A recursive tree inspection did not find an `app/src/main/assets/` directory or the expected ONNX model file on this branch. Model loading therefore cannot be confirmed and is likely to fail unless the model is packaged elsewhere by the build.
4. `VoiceIdentityFlow` captures three seconds of live microphone PCM at 16 kHz and enrolls three embeddings into encrypted app-private storage. It does not enroll the two supplied AAC recordings.
5. `MainActivity` connects live capture to embedding generation and comparison, but catches general exceptions and hides their details. This makes the root cause hard to diagnose.
6. The current voice comparison uses a fixed cosine threshold (0.72), which is explicitly a prototype and has not been calibrated on-device.
7. Liveness/dynamic phrase checks and replay detection are not implemented. Sensitive actions remain locked, which is the correct fail-closed behavior.
8. The microphone control is disabled; the actual capture flow starts from the voice identity button after biometric stage 1. The UI should make this workflow/status clearer.
9. The manifest declares RECORD_AUDIO and the code requests runtime permission. No real device test or successful APK/runtime log was available during this repository review.

## Supplied AAC recordings
The two supplied recordings are not committed to Git or bundled into the APK. Voice recordings are biometric-sensitive data, and embedding prerecorded clips as trusted enrollment would also undermine a live-owner check by allowing replay. They may be used only for a separately controlled offline diagnostic, not as proof of live presence. Enrollment should use three fresh, user-triggered microphone captures on the device.

## Required next steps
1. Add and verify the licensed, compatible ONNX speaker model under `app/src/main/assets/` (or update the loader to the actual packaged path); verify its checksum and model/runtime compatibility.
2. Replace silent exception handling with safe diagnostic logging and a visible error category (do not log raw audio or embeddings).
3. Add explicit UI states: loading, ready, requesting mic permission, recording sample 1/3, processing, enrolled, match/mismatch, and actionable error.
4. Run a clean Android build and inspect build logs; then test on the target phone with fresh live samples and collect non-sensitive logs.
5. Implement and test liveness/dynamic challenge and replay resistance before any protected action can be unlocked.
6. Calibrate the threshold with genuine and impostor samples and report false-accept/false-reject rates. Do not tune it randomly.
7. Keep the emergency stop engaged and sensitive operations blocked until these checks pass.

## Verification status
- Source review: completed for the files listed above.
- Model asset present at expected path: not found in inspected branch tree.
- Model initialization on device: not verified.
- Real microphone/model inference on device: not verified.
- APK build and installation test: not verified in this audit.
- Voice authentication suitable for security decisions: not ready.
