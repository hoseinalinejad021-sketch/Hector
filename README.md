# Hector Android — implementation increment 0.4.0

Android Studio source project, not a built APK.

## Changes in 0.4
- Removed the UI and code paths for deleting voice samples.
- Removed raw voice sample recording and storage entirely in this increment; the app has no `RECORD_AUDIO` permission and no access to voice sample files.
- Sensitive actions use a fail-closed policy requiring owner-specific voice verification, voice liveness, strong Android biometric verification, and an inactive emergency stop.
- AndroidX BiometricPrompt uses `BIOMETRIC_STRONG`; Hector does not read fingerprint images or templates.
- AES/GCM-encrypted local notes use a key held by Android Keystore.
- `HIC-STOP-01` defaults to engaged and blocks protected operations. This is an app-side software latch, not an independent physical control.
- Security settings are not exposed as mutable controls in the app UI.

## Critical status
Owner-specific speaker verification is **not implemented**. The included `VoiceVerificationGateway` deliberately fails closed. Consequently, sensitive actions remain unavailable until a vetted on-device speaker-verification model, noise handling, replay/liveness detection, and enrollment flow are integrated and tested. This avoids pretending that speech-to-text or a saved recording identifies a person.

Android users can still change app permissions or uninstall the app through Android system settings. An app cannot make itself absolutely immutable on an ordinary, unmanaged phone. The emergency stop is software-only; genuine independence requires a separate hardware controller or an independently managed device policy.

## Build
Open the `hector` folder in Android Studio, allow Gradle sync, and run on a test device. Compile SDK 35, min SDK 26, Android Gradle Plugin 8.7.3. No Gradle wrapper is included in this archive.

Package: `com.hector.assistant` — version `0.4.0`.
Do not use this prototype to protect financial accounts or authorize trades.
