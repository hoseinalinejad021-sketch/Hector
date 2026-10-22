# Changelog

Notable changes to Hector Android are recorded here.

## 1.6.0-background-voice (versionCode 26)

- Maintains the current Android app identity: `com.hector.android`, minimum SDK 23, and target SDK 36.
- Includes the Code Studio workspace and AI-assisted code workflow present on the `@Chat-GPT` branch.
- Includes the current GPT-Live voice session implementation and its network/session hardening.
- Includes the current encrypted local memory implementation and Android instrumentation-test coverage.
- Keeps release signing validation and signed-release steps restricted to an explicitly dispatched workflow on `@Chat-GPT`.
- Keeps biometric/fingerprint authentication and camera permissions out of the app's intended feature set.

## Release status

This changelog is not proof of a production release. Before distributing a signed APK, verify the latest GitHub Actions run, validate all four release-signing secrets without exposing their values, verify the APK signature and package metadata, and test installation/update, microphone and speaker behavior, cloud synchronization, notifications, and emergency stop on a physical device. Complete and publish the privacy policy and store disclosures before any store submission.

A successful debug build or emulator test alone does not certify a signed production release.
