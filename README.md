# Hector Android — integrated Persian assistant 1.4.0

Hector is an Android prototype that brings the currently usable app features into one installable project: Android system biometric prompt, a front-camera face-presence/movement challenge, Persian speech input for ordinary conversation, Persian text-to-speech when the phone supports it, encrypted local notes/chat history, configurable HTTPS AI endpoint, and the manual HIC-STOP-01 software stop.

## What is included in the APK source

- **Login:** AndroidX BiometricPrompt, followed by CameraX/ML Kit face-presence and head-movement challenge.
- **Conversation:** Persian speech recognition through the phone's installed recognition service; no direct recording of unrelated audio files. Spoken replies use Android TextToSpeech if Persian voice data is installed.
- **AI connection:** user-configured HTTPS endpoint compatible with OpenAI Chat Completions, model name, and API key. No AI model or API key is embedded in the APK. Without a configured endpoint/key, messages are saved locally but no remote AI response is generated.
- **Encrypted persistence:** local notes, chat history, and API key use AES-GCM with an Android Keystore key.
- **HIC-STOP-01:** manual in-app stop locks the session, attempts to persist the stop flag, disconnects active network requests, ignores stale replies, and stops speech output. Clearing the stop requires explicit user confirmation and a successful full login; a failed persistence write is treated as a failure.
- **Build checks:** JVM tests for the fail-closed sensitive-operation gate, source/security checks, APK packaging, and signing-certificate verification.

## Explicit limitations

- BiometricPrompt chooses a supported device modality. The app cannot force fingerprint-only on every phone.
- The camera challenge checks face presence and head movement; it is **not** a comparison against a stored owner-face template and must not be described as independent face identity verification.
- The archived speaker-identity prototype in `legacy/voice_identity_prototype/` is intentionally not compiled into the APK. Its ONNX model asset is absent and liveness/replay resistance are not validated. It is not used for login. This matches the active fingerprint/biometric + camera flow rather than enabling an incomplete voice-authentication path.
- No sensitive financial/device-control actions are implemented as authorized actions. `OwnerVerificationGate` defaults to deny unless every identity, liveness, consent, and stop-state requirement is explicitly satisfied.
- CI success proves the configured build/tests/artifact checks only. It does not prove installation or runtime behavior on the target phone.

## Build

GitHub Actions uses JDK 17, Android SDK 35, and Gradle 8.9. A successful run uploads a debug APK artifact named `Hector-debug-APK` for seven days.

- Package: `com.hector.android`
- Version: `1.4.0-integrated` (`versionCode 14`)
- Android Gradle Plugin: `8.7.3`
- Kotlin Gradle plugin: `2.0.21`
- Compile/target SDK: `35`; minimum SDK: `23`

**Update compatibility:** a debug APK can replace an existing install only when the signing certificate matches. GitHub-hosted signing-key caching is not a substitute for a privately retained release key. Do not expect every independently built debug artifact to update in place.

Do not use this prototype as production-grade authentication for financial accounts or sensitive operations.
