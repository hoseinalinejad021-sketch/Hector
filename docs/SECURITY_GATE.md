# Hector security gate and release checklist — 1.4.0

## Active app flow
1. AndroidX BiometricPrompt checks a biometric enrolled with the device. Android chooses the supported modality; the app cannot guarantee fingerprint-only behavior across phones.
2. A front-camera CameraX/ML Kit challenge checks for one face, a centered pose, a head turn and return to center. This is a presence/movement check, not comparison with a stored owner-face template and not a production anti-spoof system.
3. Persian speech recognition is used for ordinary conversation only and is delegated to the installed Android recognition service. TextToSpeech provides spoken replies when Persian voice data is available. Neither feature verifies speaker identity.
4. Remote AI replies require a user-configured HTTPS endpoint, model name and API key. No model or API key is embedded in the APK. The conversation history and local memory can be sent to that user-selected endpoint as part of a chat request.
5. Local notes, chat history and API key are stored using AES-GCM with an Android Keystore key. Android backup is disabled, cleartext traffic is disabled and screenshots/recents previews are blocked using FLAG_SECURE.

## HIC-STOP-01
- The manual in-app stop locks the current session, disconnects an active request, invalidates stale replies and stops speech output.
- Stop state is persisted synchronously when engaged. If persistence fails, the app remains locked and displays a warning.
- Clearing a persisted stop requires separate explicit confirmation and completion of the full login flow. If clearing the flag cannot be persisted, the code attempts to restore the stop flag and denies access.
- This is an in-app software latch, not a physical switch or protection against app deletion/device compromise. The app does not change Android security settings.

## Fail-closed policy
- `OwnerVerificationGate` defaults every evidence item to false and emergency stop to active.
- Sensitive operations must remain unavailable unless all required identity, liveness, explicit-consent and stop-state evidence is independently established. The current app does not implement production-grade owner-face or speaker identity matching, so those proofs are not available.
- The old `SpeakerModelEngine` and `VoiceIdentityFlow` are preserved outside the Android source set. The ONNX model is missing and liveness/replay resistance are unvalidated; they must not be compiled in or used to authorize actions until properly implemented and tested.
- The app does not request unrelated audio-file access or store raw speech-recognition audio. The external speech-recognition service may process speech under its own policies.

## Release checks
- CI: JVM tests, project/security source checks, APK build, signer verification and artifact existence.
- On the actual phone: installation/upgrade, first launch, biometric failure/cancel, camera permission denial, camera timeout, stop persistence across restart, background relocking, AI endpoint errors, encrypted-memory persistence, speech cancellation and Persian TTS.
- CI success does not prove real-device installation, runtime correctness, API availability or production security.
