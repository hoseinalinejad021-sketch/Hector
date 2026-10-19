# Hector security gate and release checklist — stop-key-only access

## Active app flow
1. The app opens directly to the home screen. There is no fingerprint, face, liveness, voice-identity, or other biometric login gate.
2. HIC-STOP-01 is the only in-app control intended to stop the current session. Activating it cancels active network work, stops speech output, and persists the stopped state.
3. When the stop latch is active, the app remains on a stopped screen until the user explicitly confirms that work may resume. No identity check is required to resume.
4. Remote AI replies require a user-configured HTTPS endpoint and API key. No API key is embedded in the APK. Conversation history and local memory may be sent to the configured endpoint as part of a chat request.
5. Local notes, chat history and API key are stored using AES-GCM with an Android Keystore key. Android backup is disabled, cleartext traffic is disabled and screenshots/recents previews are blocked using FLAG_SECURE.

## HIC-STOP-01
- The manual in-app stop locks the current session, disconnects an active request, invalidates stale replies and stops speech output.
- Stop state is persisted synchronously when engaged. If persistence fails, the app remains locked and displays a warning.
- Clearing a persisted stop requires only separate explicit user confirmation; there is no login or identity-check step. If clearing the flag cannot be persisted, the code attempts to restore the stop flag and denies access.
- This is an in-app software latch, not a physical switch or protection against app deletion/device compromise. The app does not change Android security settings.

## Fail-closed policy
- `OwnerVerificationGate` defaults every evidence item to false and emergency stop to active.
- The app does not require identity, liveness, face, voice-identity, or biometric evidence to open. The only access stop is the user-controlled HIC-STOP-01 latch.
- The old `SpeakerModelEngine` and `VoiceIdentityFlow` are preserved outside the Android source set. The ONNX model is missing and liveness/replay resistance are unvalidated; they must not be compiled in or used to authorize actions until properly implemented and tested.
- The app does not request unrelated audio-file access or store raw speech-recognition audio. The external speech-recognition service may process speech under its own policies.

## Release checks
- CI: JVM tests, project/security source checks, APK build, signer verification and artifact existence.
- On the actual phone: fresh installation and upgrade, first launch directly to home, microphone permission granted/denied/revoked, text-only use without microphone permission, stop persistence across restart, background/foreground transitions, AI endpoint errors, encrypted-memory persistence, speech cancellation and Persian TTS. Do not test biometric login or camera login: neither is part of the current stop-only design.
- Before public distribution, complete the checklist in `docs/ANDROID_INSTALL_RELEASE_CHECKLIST_FA.md`, verify the latest CI run, confirm the release signing certificate, and complete applicable privacy/Google Play declarations. Repository files cannot grant external approvals.
- CI success does not prove real-device installation, runtime correctness, API availability or production security.
