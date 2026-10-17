# Hector 1.3.6 build status

- App version: 1.3.6-speech-integration-fix (versionCode 10).
- Main branch of this work: integrate-speech-ai-2026-10-17; PR #6 targets @Chat-GPT.
- Implemented in source: AndroidX Activity Result speech launcher dependency, Persian system speech input, Persian TTS output when device voice data is available, encrypted local memory/API key, HTTPS-only AI endpoint, persisted emergency-stop state, and cancellation of in-flight AI requests when locking.
- AI replies require a user-supplied HTTPS endpoint, compatible model name, and valid API key. The APK does not include an AI model or API key.
- Login is Android device biometrics, not a face-specific identity match. The local speaker-embedding prototype is not used for login; its ONNX model asset is missing and liveness/replay protection is not implemented.
- HIC-STOP-01 is an in-app software stop. It locks the current session and cancels its active request, but is not a physical or OS-independent emergency control.
- CI run for commit 73f31328bb20b43ed10386585e24a552ea71956f completed successfully, built a debug APK, and verified its signature. That APK was 167,684,149 bytes before ABI filtering.
- A follow-up change limits the APK to arm64-v8a to reduce size. The build for that exact newer commit must complete before its artifact is treated as current.
- No physical-device install, Persian voice-data, real API connection, restart-persistence, or security test has been run in this environment.
