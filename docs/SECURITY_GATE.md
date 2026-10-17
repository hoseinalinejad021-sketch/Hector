# Hector security gate and release checklist

## Active login and conversation flow
1. The app requests AndroidX BiometricPrompt using a device-supported biometric. Android controls the modality; the app cannot guarantee fingerprint specifically or a separate face-only second factor.
2. Successful device biometric authentication starts a front-camera challenge. The app requires a face near center, a fresh head turn and a return to center using CameraX/ML Kit face detection. This is a liveness-style movement check, not a match to a stored owner-face template.
3. Before opening the conversation UI, the app generates a random three-word Persian phrase and compares the Android speech-recognition result with that phrase. This is challenge-response, not speaker identity verification or robust anti-spoof detection. Normal conversation speech is still delegated to Android speech recognition.
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
- Biometric cancellation/failure, camera permission denial, face challenge timeout/failure, speech-recognition cancellation or phrase mismatch leaves the app locked.
- AI endpoints must use HTTPS; cleartext HTTP is rejected.
- The app does not store raw audio or request unrelated audio-file access. The Android speech-recognition service may process the challenge according to its own settings and privacy policy.
- Android backups are disabled; screenshots/recents previews are blocked with FLAG_SECURE.
- A successful CI build and APK signature check do not prove physical-device installation, API connectivity, Persian TTS availability, memory persistence after restart, or security correctness.

## Release blockers
- Install and launch on the target phone; test biometric success/failure/cancellation and app-background relocking.
- Test speech recognition cancellation, unavailable recognition service, and Persian TTS missing-data behavior.
- Configure and test a real HTTPS AI endpoint; verify error handling and that lock cancels in-flight requests.
- Test encrypted-memory persistence after app restart and stop activation.
- Do not describe the app as face-authenticated or production-grade secure until dedicated face matching and independent security testing exist.\n\n## Owner face/voice challenge implementation (v1.3.8)\n- Source: `FaceLivenessChallengeController.kt` and the staged authentication flow in `MainActivity.kt`.\n- The device biometric stage is controlled by Android and cannot be forced to use fingerprint or face only.\n- No custom owner-face embedding/template matcher is implemented. No speaker-embedding matcher or validated anti-spoof model is active.\n- Do not authorize sensitive actions from the camera movement or spoken phrase checks. Keep such actions disabled until independently validated face/voice models and device tests exist. See `docs/OWNER_FACE_VOICE_CHALLENGE_FA.md`.\n

## Security hardening follow-up (v1.3.9 candidate)
- HIC-STOP-01 is now a persistent latch: resuming after a stop requires a separate explicit confirmation and then the complete login flow. Cancel/failure keeps the stop active.
- When the app backgrounds during an unlocked session (except while speech recognition is intentionally in front), the session generation is invalidated, active HTTP request is disconnected, stale replies are ignored, and speech output stops.
- AI requests reject non-HTTPS URLs, URLs without a host, and URLs containing user-info credentials; automatic HTTP redirects are disabled to reduce accidental API-key forwarding.
- These are code-level hardening changes; CI compilation and physical-device tests are still required. The app is not production-certified.
