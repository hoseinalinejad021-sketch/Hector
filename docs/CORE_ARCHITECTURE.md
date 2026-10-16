# Hector 1.3 Core Architecture

## Release and update compatibility
- Keep applicationId `com.hector.android` unchanged so Android recognizes future builds as updates to the same app.
- Increase `versionCode` for every release and keep `versionName` human-readable.
- Android only installs an update over an existing installation when the signing certificate matches. GitHub Actions debug signing keys are not guaranteed to persist across runners; therefore APKs from separate runs may not update an existing install. A stable private signing key must be kept outside the repository (for example, protected CI secrets) before promising repeatable in-place updates. Never commit a private keystore or its password.
- Do not use an automatic self-updater that bypasses Android's package installer. Updates must be user-approved by Android.

## Entry and identity gates
1. Android `BIOMETRIC_STRONG` prompt and an Android Keystore cryptographic operation.
2. Owner-specific voice verification from live audio, with enrollment, challenge-response, replay resistance, and liveness checks.
3. Protected features remain locked until both checks are implemented and tested. A microphone permission, speech-to-text result, or spoken passphrase is not voice identity verification.
4. The current UI displays both stages, but the voice stage is intentionally unavailable and protected access remains locked. Do not advertise voice login as working until a real speaker-verification engine is integrated and tested.

## Required security core
- `HIC-STOP-01`: manual emergency stop; default locked state; lock again on backgrounding, restart, cancellation, errors, or failed checks.
- Do not access unrelated audio files; request microphone access only after explicit user action.
- No automatic security-setting changes, self-granted permissions, or hidden background recording.
- Encrypted local memory should use Android Keystore-backed encryption; sensitive data should be minimized and cleared when no longer needed.
- Network/API integration must use TLS, explicit consent, least-privilege permissions, and server-side secret storage. Never embed private API keys in the APK.
- Battery-health features must be informational and use public Android APIs; no claims of antivirus protection without a separately tested scanner.

## Continuous integration
- Every push should run the Android CI workflow; accept a release candidate only when compilation and APK artifact verification pass.
- CI success does not validate the installed APK signature or on-device behavior; verify those separately before an in-place update.

## Release test checklist
- Confirm package ID and signing certificate match the currently installed app before expecting an in-place update.
- Build the APK in CI and verify the artifact exists.
- On the actual phone, test upgrade without uninstalling, first launch, restart, biometric failure/cancellation, microphone denial, stop activation, and locked-state persistence.
- Verify that network and sensitive actions remain unavailable until both identity gates pass.
- Treat a successful build as compilation evidence, not a security certification.
