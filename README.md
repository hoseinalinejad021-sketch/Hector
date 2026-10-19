# Hector Android — stop-only build 1.5.1

Hector is an Android personal assistant with Persian text/speech features, encrypted local memory, an optional online AI connection configured by the user, and the explicit emergency-stop control `HIC-STOP-01`.

## Current build

- Package: `com.hector.android`
- Version: `1.5.1-stop-only` (`versionCode 25`)
- Android: minimum SDK 23; compile/target SDK 36 (Android 16)
- Login: no fingerprint, face, voice, or passcode gate. The app opens directly unless the persisted emergency stop is active.
- Emergency stop: `HIC-STOP-01` stops the session and requires explicit confirmation to resume.
- Local storage: memory notes, conversation history, and the AI API key are encrypted using Android Keystore and AES-GCM.
- Network: HTTPS only, redirects disabled, active requests cancelled when the session is stopped/backgrounded.
- Screen capture: Android `FLAG_SECURE` is enabled.
- Persian speech: uses the phone's speech-recognition service for input and the bundled Persian TTS engine with system TTS fallback for output.
- AI: the user must configure their own endpoint, model, and API key in the app. No API key is embedded in the APK. The default endpoint is `https://api.openai.com/v1/responses`; online responses require a valid key and a model available to that account.

## Build and download

For private installation on your own Android phone (without Google Play), see [the Persian private-install guide](docs/PRIVATE_ANDROID_INSTALL_FA.md) the [install/release checklist](docs/ANDROID_INSTALL_RELEASE_CHECKLIST_FA.md), and the [pre-install gap audit](docs/ANDROID_PREINSTALL_GAP_AUDIT_FA.md).

GitHub Actions runs Android Lint, JVM tests, Android instrumentation tests on an emulator, builds a diagnostic debug APK, and verifies its signature. The debug APK is uploaded only as a short-lived CI artifact and is not labeled an official release. A signed release APK is published only when the four release-signing secrets below are configured in GitHub Actions.

The latest release page is:
https://github.com/hoseinalinejad021-sketch/Hector/releases

To enable official signed releases, configure these repository Actions secrets: `HECTOR_RELEASE_KEYSTORE_BASE64` (base64 encoding of the private release keystore), `HECTOR_RELEASE_STORE_PASSWORD`, `HECTOR_RELEASE_KEY_ALIAS`, and `HECTOR_RELEASE_KEY_PASSWORD`. Keep the keystore and passwords private and backed up securely. The workflow will not publish a debug APK as an official release if these secrets are missing. The repository is private, so a GitHub account with repository access is required to download release assets.

## Security and compatibility notes

- This build intentionally has no login gate; the emergency-stop control is not an identity/authentication mechanism.
- `FLAG_SECURE` reduces screenshots and recent-app previews but does not protect against every possible capture method.
- The AI API key is stored in app-private encrypted storage and must be supplied by the user.
- A successful CI build and signature check do not prove that the APK installs or runs correctly on every target phone.
- Debug APK updates require the same package ID and signing certificate as the installed build. Do not uninstall an existing installation unless you are prepared for any app-private local data to be lost.
- Do not treat this prototype as production-grade security for financial accounts or sensitive device control.


## Release validation documents

- [Device-test report template](docs/DEVICE_TEST_REPORT_TEMPLATE_FA.md)
- [Privacy policy draft (must be completed and hosted publicly before store submission)](docs/PRIVACY_POLICY_FA.md)
- [Google Play listing and release checklist](docs/PLAY_STORE_LISTING_FA.md)
- [Changelog](CHANGELOG.md)

Emulator tests are automated, but they do not replace microphone/speaker, real API account, update-install, and device-specific testing on a physical phone. Record that evidence in the device-test report. A public release also requires a public privacy-policy URL and completion of store disclosures by the publisher.
