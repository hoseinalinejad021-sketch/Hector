# Hector security gate and release checklist

## Active login and conversation flow
1. The app requests AndroidX BiometricPrompt using a device-supported biometric. Android controls the modality; the app cannot guarantee fingerprint specifically or a separate face-only second factor.
2. Successful device biometric authentication opens the conversation UI. This is device-level authentication, not a custom identity match to the owner.
3. Persian speech input is started only after the user taps the speech button and is delegated to the Android recognition service. The active app does not request direct microphone access or read audio files from storage.
4. AI responses require a user-configured HTTPS endpoint, model name and API key. API keys and local memory are stored encrypted using AES-GCM with Android Keystore. No credentials are embedded in the repository or APK.
5. Persian spoken output depends on Android TextToSpeech and installed Persian voice data; unsupported/missing voice data is reported to the user.

## HIC-STOP-01
- The user can manually engage the in-app stop at any time from the conversation screen.
- Engaging it persists a stop flag, returns the app to the login screen, disconnects an active AI request, suppresses stale replies and stops current speech output.
- A successful subsequent biometric login clears the in-app stop flag.
- This is a software latch inside the app, not a physical emergency switch or protection against app deletion/device compromise. The app does not change Android security settings.

## Archived speaker identity prototype
- Experimental SpeakerModelEngine and VoiceIdentityFlow source is preserved under `legacy/voice_identity_prototype/`, outside the Android source set.
- The expected ONNX model is not packaged; model integrity, dynamic spoken challenge, liveness, replay resistance, threshold calibration and device testing are incomplete.
- The prototype is not used for login or authorization and must not be re-enabled until the missing model and security tests are addressed.

## Fail-closed and privacy rules
- Biometric cancellation/failure leaves the app locked.
- AI endpoints must use HTTPS; cleartext HTTP is rejected.
- Raw audio is not captured by the active app or written to disk. No unrelated audio-file permission is requested.
- Android backups are disabled; screenshots/recents previews are blocked with FLAG_SECURE.
- A successful CI build and APK signature check do not prove physical-device installation, API connectivity, Persian TTS availability, memory persistence after restart, or security correctness.

## Release blockers
- Install and launch on the target phone; test biometric success/failure/cancellation and app-background relocking.
- Test speech recognition cancellation, unavailable recognition service, and Persian TTS missing-data behavior.
- Configure and test a real HTTPS AI endpoint; verify error handling and that lock cancels in-flight requests.
- Test encrypted-memory persistence after app restart and stop activation.
- Do not describe the app as face-authenticated or production-grade secure until dedicated face matching and independent security testing exist.