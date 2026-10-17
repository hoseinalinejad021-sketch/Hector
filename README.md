# Hector Android — Persian speech chat 1.3.7

This repository contains the Android source project for a personal assistant prototype. The active app provides device biometric login, encrypted local memory and API-key storage, Persian speech input through the phone's installed speech-recognition service, and spoken replies through Android TextToSpeech when Persian voice data is available.

## AI connection
The APK does not contain an AI model or API key. To receive actual model replies, the user must configure an HTTPS endpoint compatible with OpenAI Chat Completions, a model name, and a valid API key. The key is stored in app-private storage encrypted with Android Keystore/AES-GCM. Without this configuration, messages are saved locally but no remote AI response is generated.

## Security and limitations
- HIC-STOP-01 is an in-app software stop: it locks the session, cancels the active AI request, and requires biometric re-authentication. It is not a physical or OS-independent control.
- Android BiometricPrompt uses a biometric supported by the device. The app cannot force a second prompt to be face-only or match the owner's face independently. Face-specific enrollment/matching is not implemented.
- Conversational speech recognition is separate from voice authentication. The active app delegates speech input to the installed recognition service and does not directly record audio or read unrelated audio files.
- The experimental speaker-identity prototype is preserved under `legacy/voice_identity_prototype/` but is not compiled into the APK. Its ONNX model asset, liveness detection, replay resistance, and device validation are not complete. It must not authorize sensitive operations.
- Local memory and API keys are encrypted using AES-GCM with an Android Keystore key. Android backups and cleartext HTTP traffic are disabled; `FLAG_SECURE` blocks screenshots/recents previews.
- CI success proves source compilation, APK production, and the configured signing check only. It does not prove installation or runtime behavior on the target phone.

## Build
GitHub Actions uses JDK 17, Android SDK 35, and Gradle 8.9. A successful run uploads a debug APK artifact named `Hector-debug-APK` for seven days. The active app does not package the unused sherpa-onnx native runtime, keeping the APK much smaller and architecture-independent.

- Package: `com.hector.android`
- Version: `1.3.7-speech-chat-cleanup` (`versionCode 11`)
- Android Gradle Plugin: `8.7.3`
- Kotlin Gradle plugin: `2.0.21`
- Compile/target SDK: `35`
- Minimum SDK: `23`

Do not use this prototype as production-grade authentication for financial accounts or sensitive operations.