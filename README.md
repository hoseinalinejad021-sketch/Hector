# Hector Android — stop-only build 1.5.0

Hector is an Android personal assistant with Persian text/speech features, encrypted local memory, an optional online AI connection configured by the user, and the explicit emergency-stop control `HIC-STOP-01`.

## Current build

- Package: `com.hector.android`
- Version: `1.5.0-stop-only` (`versionCode 24`)
- Android: minimum SDK 23; compile/target SDK 35
- Login: no fingerprint, face, voice, or passcode gate. The app opens directly unless the persisted emergency stop is active.
- Emergency stop: `HIC-STOP-01` stops the session and requires explicit confirmation to resume.
- Local storage: memory notes, conversation history, and the AI API key are encrypted using Android Keystore and AES-GCM.
- Network: HTTPS only, redirects disabled, active requests cancelled when the session is stopped/backgrounded.
- Screen capture: Android `FLAG_SECURE` is enabled.
- Persian speech: uses the phone's speech-recognition service for input and the bundled Persian TTS engine with system TTS fallback for output.
- AI: the user must configure their own endpoint, model, and API key in the app. No API key is embedded in the APK. The default endpoint is `https://api.openai.com/v1/responses`; online responses require a valid key and a model available to that account.

## Build and download

GitHub Actions runs the JVM tests, checks security defaults, builds the debug APK, verifies its signature, and publishes the APK as a GitHub Release asset.

The latest release page is:
https://github.com/hoseinalinejad021-sketch/Hector/releases

The workflow's current release asset is named `app-debug.apk`. The repository is private, so a GitHub account with repository access is required to download it.

## Security and compatibility notes

- This build intentionally has no login gate; the emergency-stop control is not an identity/authentication mechanism.
- `FLAG_SECURE` reduces screenshots and recent-app previews but does not protect against every possible capture method.
- The AI API key is stored in app-private encrypted storage and must be supplied by the user.
- A successful CI build and signature check do not prove that the APK installs or runs correctly on every target phone.
- Debug APK updates require the same package ID and signing certificate as the installed build. Do not uninstall an existing installation unless you are prepared for any app-private local data to be lost.
- Do not treat this prototype as production-grade security for financial accounts or sensitive device control.
