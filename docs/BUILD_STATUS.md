# Hector 1.3.7 build status

- App version: 1.3.7-speech-chat-cleanup (versionCode 11).
- Active work branch: integrate-speech-ai-2026-10-17; PR #6 targets @Chat-GPT.
- Active source includes AndroidX Activity Result integration, Persian speech input through the installed Android recognition service, Persian TextToSpeech output when voice data is available, encrypted local memory/API-key storage, HTTPS-only AI endpoint validation, persistent in-app HIC-STOP-01 state, and cancellation/ignoring of stale AI requests after locking.
- AI replies require a user-supplied HTTPS endpoint, compatible model name, and valid API key. The APK contains no AI model or API key.
- The unused speaker-identity prototype has been preserved under legacy/voice_identity_prototype/ and removed from the Android app source set. Its ONNX model, liveness and replay protection remain unimplemented; it cannot authorize access.
- Android biometric login does not guarantee fingerprint-then-face or a face-specific match to the owner.
- HIC-STOP-01 is an in-app software stop, not a physical or OS-independent emergency control.
- CI run for commit 73f31328bb20b43ed10386585e24a552ea71956f succeeded and verified a debug APK signature, but that APK included the unused native voice runtime and was 167,684,149 bytes. The newest changes remove that dependency and require a fresh successful build before their artifact is current.
- No physical-device install, Persian voice-data, live AI endpoint, restart-persistence, or security test has been run in this environment.