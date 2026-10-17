# Hector Android — voice-matching prototype 1.3.4-install-fix

This repository contains the Android source project. A debug APK is available only after the GitHub Actions build completes successfully.

## Current implementation

- AndroidX `BiometricPrompt` requests `BIOMETRIC_STRONG` and binds authentication to an Android Keystore AES-GCM operation. Hector never receives fingerprint images or biometric templates; Android may support a strong biometric other than fingerprint.
- `SecureMemoryStore` provides app-private encrypted storage using AES/GCM and an Android Keystore-managed key. This is a storage component, not proof that every app datum is persisted or that the caller is authenticated.
- `SpeakerModelEngine` loads a local sherpa-onnx speaker-embedding model and exposes embedding computation. The model engine alone does not establish a person's identity.
- `HIC-STOP-01` is a software emergency-stop latch, not a physical or hardware-independent control.
- Microphone capture is explicitly user-triggered and requires runtime permission. Internet permission is declared for possible future integration; it does not mean a network or AI backend is implemented.
- Cleartext HTTP traffic and Android backup are disabled. `FLAG_SECURE` is used to block screenshots and recents previews.

## Not implemented or not verified

Local microphone capture, speaker-embedding comparison, and encrypted storage of an averaged voice profile are present as a prototype. Enrollment now clears temporary embedding arrays even when a sample or save operation fails, and profile parsing validates the stored dimensions and values. These changes have not been validated end to end on the target phone. Voice liveness, replay detection, noise robustness, threshold calibration, and the complete authorization state machine are not implemented/validated. A local voice enrollment/comparison may run while the emergency-stop latch remains active; this does not authorize network or sensitive operations. The model runs locally on-device after the model asset is packaged; voice processing does not require a live server connection. The workflow downloads the model during the build, so the build environment needs internet, but runtime inference is intended to be offline. The voice gate must therefore remain fail-closed; protected/network operations must remain blocked.

Do not use this prototype to protect financial accounts or authorize trades.

A successful CI build demonstrates compilation and configured checks only. It does not prove runtime security, voice identity accuracy, on-device behavior, or compatibility with an already-installed APK.

## Build and version

- Package: `com.hector.android`
- Version: `1.3.4-install-fix` (`versionCode 8`)
- Android Gradle Plugin: `8.7.3`
- Kotlin Gradle plugin: `2.0.21`
- Gradle: `8.9`
- JDK: `17`
- Compile/target SDK: `35`
- Minimum SDK: `23`
- Speaker runtime dependency: sherpa-onnx `v1.13.8`

GitHub Actions runs on pushes and can be manually dispatched. On a successful run, the workflow uploads the debug APK as `Hector-debug-APK` with a seven-day retention period. The workflow downloads the speaker model during CI; model availability and runtime compatibility still require successful build and device testing.

## Upgrade warning

Android allows an in-place update only when the package ID matches and the new APK is signed with the same signing certificate as the installed app. Debug builds from different CI runners may not share a stable signing key. Verify the certificate before installing an update; do not uninstall the existing app just to work around a signature mismatch.

## Security test checklist

- [ ] CI build succeeds and APK artifact is present.
- [ ] Install and launch on the target Android phone.
- [ ] Confirm emergency stop remains active after restart.
- [ ] Confirm missing/cancelled biometric authentication leaves protected actions locked.
- [ ] Confirm microphone permission denial does not unlock any feature.
- [ ] Confirm no unrelated audio files are accessed.
- [ ] Confirm encrypted-memory write/read and persistence across app restart.
- [ ] Confirm the speaker model loads on-device; do not treat embedding output alone as identity verification.
- [ ] Test upgrade signature compatibility before attempting an in-place update.

Items remain unchecked until actual test evidence is recorded.

## Voice identity prototype
- After strong device biometrics, the user can enroll three short voice samples or compare a new sample locally.
- Only the encrypted speaker-embedding profile is persisted; raw audio is processed in memory. Temporary PCM and embedding buffers are cleared in `finally` paths when inference or enrollment fails.
- Voice matching is currently a prototype. The threshold is not calibrated, and spoken-challenge recognition, liveness and anti-replay checks are not implemented.
- A voice match does not unlock sensitive features or network access. Physical-device testing is still required.


## Android update-signing note

GitHub-hosted debug builds previously used a newly generated debug certificate per run, which causes Android to reject an APK as an update because the signing certificate differs. The workflow now caches `~/.android/debug.keystore` for subsequent builds. The first build using this fix establishes the stable key; the currently installed APK signed by the previous key cannot be updated in place and must be removed before installing this migration build. This is a debug-only distribution path, not a production signing setup. The cache can expire, so a durable long-term solution is a private release keystore stored in GitHub Actions secrets.
