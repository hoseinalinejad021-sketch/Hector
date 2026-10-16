# Hector Android — security-gate prototype 1.2

Android Studio source project. A built APK is produced only after the GitHub Actions build succeeds.

## Security behavior
- AndroidX BiometricPrompt requires `BIOMETRIC_STRONG`; Hector does not read fingerprint images or templates.
- AES/GCM-encrypted local notes use a key held by Android Keystore.
- `HIC-STOP-01` starts engaged and blocks protected operations. It is a software latch, not an independent physical control.
- The app has no `RECORD_AUDIO` or `INTERNET` permission and does not access unrelated audio files.
- Security settings are not exposed as mutable controls in the app UI.

## Important limitation
Owner-specific speaker verification, voice liveness, noise-resistant enrollment, and replay detection are **not implemented**. The voice-verification gateway fails closed; sensitive actions remain locked. Do not use this prototype to protect financial accounts or authorize trades.

Android users can still change app permissions or uninstall the app through system settings. An ordinary app cannot make itself absolutely immutable on an unmanaged phone.

## Build
Open the project in Android Studio, allow Gradle sync, and run on a test device. The project uses JDK 17, Android Gradle Plugin 8.7.3, compile SDK 35, and min SDK 23. No Gradle wrapper is included.

Package: `com.hector.android` — version `1.2-security-gate` (versionCode 3).
