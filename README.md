# Hector Android — security-gate prototype 1.3.0-core

This repository contains the Android source project. A debug APK is available only after the automated GitHub Actions build succeeds.

## Security behavior
- AndroidX BiometricPrompt requests `BIOMETRIC_STRONG`; Hector does not read fingerprint images or templates.
- Local challenge data is protected using AES/GCM and a key held by Android Keystore.
- `HIC-STOP-01` is a software emergency-stop latch. It is not a physical or hardware-independent control.
- The app requests microphone and internet permissions in preparation for gated features; this does **not** mean voice identity verification or network features are implemented.
- Cleartext HTTP traffic is disabled and Android backup is disabled.

## Not yet implemented / not verified
Owner-specific speaker verification, voice enrollment, voice liveness, replay detection, and noise-resistant voice matching are **not implemented**. Until these are implemented and tested, the voice gate must fail closed and sensitive/network operations must remain blocked. This prototype must not be used to protect financial accounts or authorize trades.

A successful CI build proves compilation and configured checks only. It does not prove runtime security, APK signature compatibility with an already-installed copy, or device-level behavior.

## Build
GitHub Actions runs on pushes and can be manually dispatched. It uses JDK 17, Android Gradle Plugin 8.7.3, Gradle 8.9, compile SDK 35, and min SDK 23. The workflow uploads `Hector-debug-APK` for seven days after a successful build.

Package: `com.hector.android` — version `1.3.0-core` (versionCode 4).

## In-place upgrade warning
Android permits an in-place update only when the new APK is signed with the same signing certificate as the installed app (and uses the same package ID). Debug builds produced on different CI runners may not share a stable signing key. Verify the signing certificate before installing an update; do not uninstall the existing app merely to work around a signature mismatch.
