# Hector Android architecture — 1.4.1-special-hardening

## Package and update compatibility
- Application ID remains `com.hector.android`.
- Version `1.4.1-special-hardening`, versionCode 15.
- Android only accepts an in-place update when the signing certificate matches. GitHub debug-key caching is not a substitute for a privately retained release key. Never commit private signing keys or passwords.

## Active integrated components
- `MainActivity.kt`: login/session state, chat UI, AI endpoint settings, Persian speech input, TextToSpeech, encrypted-memory calls, and HIC-STOP-01.
- `FaceLivenessChallengeController.kt`: CameraX/ML Kit face presence and movement challenge.
- `SecureMemoryStore.kt`: AES-GCM persistence backed by Android Keystore.
- `OwnerVerificationGate.kt`: fail-closed policy for sensitive actions; all evidence defaults deny.
- `OwnerVerificationGateTest.kt`: JVM tests verify default denial and denial whenever a required proof is absent or the emergency stop is active.

## Identity boundary
The app uses Android BiometricPrompt with `BIOMETRIC_STRONG` and then a camera movement challenge. Android controls the biometric modality; camera movement is not owner-face matching. Voice input/output is for conversation only. The archived voice identity prototype is excluded from the APK because its model asset and validated liveness/replay protections are missing. Do not enable sensitive actions based on these incomplete factors.

## Security requirements
- HIC-STOP-01 is manual, fail-closed, and does not alter OS security settings. An in-process latch prevents re-login if persisting the stop flag fails during the current process.
- Backgrounding invalidates the session and cancels in-flight AI requests.
- No access to unrelated audio files or background microphone recording.
- HTTPS only; reject credential-bearing URLs and disable automatic redirects.
- No API keys or model credentials embedded in source/APK.
- Encrypted local memory and Android backup disabled.

## CI and release
Every change runs JVM unit tests, source/security checks, Android debug build, APK signing-certificate verification and artifact upload. Before calling a build phone-ready, install it on the actual device and test login/cancellation, emergency stop and restart persistence, background relocking, chat/API behavior, speech and memory. CI alone is not a device test.
