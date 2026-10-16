# Hector security gate and release checklist

## Required unlock order
1. Android strong biometric prompt. The operating system verifies the enrolled biometric; Hector never receives biometric templates. Android may choose another supported strong biometric modality, so the app cannot guarantee fingerprint-only behavior on every phone.
2. The biometric prompt is bound to an Android Keystore AES-GCM operation. A fresh random challenge must be processed by the authorized cipher; failure, cancellation, missing key, or missing proof keeps the app locked.
3. Owner-specific voice verification using a live challenge plus replay/liveness checks.
4. Only after all required identity checks pass may protected network features be enabled.

## Fail-closed rules
- Any cancellation, mismatch, missing hardware, timeout, cryptographic error, app backgrounding, or process restart returns to the locked state.
- HIC-STOP-01 keeps protected features locked until the full identity flow is genuinely implemented and passed.
- No unrelated audio-file access. Microphone permission is declared but requested only after the user taps the microphone button; denying it does not unlock anything.
- Do not use speech-to-text or a spoken passphrase as speaker identity; either can be replayed.
- The CI build downloads the official sherpa-onnx speaker embedding model into `app/src/main/assets` as a build input. This packages the speaker embedding model in the APK, but the app does not yet load or run inference with it; the second identity gate remains locked. No network capability may be advertised as unlocked.
- Security settings must not be changed automatically by the app.
- Android app backups are disabled; cleartext HTTP traffic is disabled; screenshots/recents previews are blocked by FLAG_SECURE.
- The manifest requests INTERNET for future secure service integration. RECORD_AUDIO is declared but the app asks for it only after the user taps the microphone button.
- Internet permission alone does not connect AI tools or provide a backend. No API keys are embedded in the APK and no AI/network service is integrated yet.
- The microphone button currently requests permission only; it does not record, upload, enroll, or verify voice.

## Implemented in this build
- AndroidX BiometricPrompt restricted to BIOMETRIC_STRONG.
- Biometric result is tied to an Android Keystore AES-GCM cipher; a fresh 32-byte challenge must be processed successfully after authentication.
- Key invalidation on biometric enrollment changes where supported by the Android version.
- Locked two-stage screen and HIC-STOP-01 emergency-stop button.
- Temporary challenge bytes are cleared on completion, error, stop, and backgrounding.
- Screenshot/recents-preview protection, backups disabled, and cleartext traffic disabled.
- CI checks for core security defaults, downloads/verifies the speaker embedding model asset, and compiles the APK.

## Not yet implemented or verified
- Runtime integration of the packaged speaker embedding model, owner-specific voice enrollment, and speaker verification.
- Liveness and replay-attack detection for voice.
- Encrypted memory and any network integration.
- Physical-device testing, including restart, biometric failure, cancellation, key invalidation, and emergency-stop regression tests.
- Independent security review. A successful build is not a security certification.

The debug APK can be built in GitHub Actions. A successful build confirms compilation and artifact creation only; it does not replace security review or on-device testing.
