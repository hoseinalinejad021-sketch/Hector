# Hector security gate and release checklist — stop-key-only access

## Active app flow
1. The app opens directly to the home screen. There is no fingerprint, face, liveness, voice-identity, or other biometric login gate.
2. HIC-STOP-01 is the only in-app control intended to stop the current session. Activating it cancels active network work, stops speech output, and persists the stopped state.
3. When the stop latch is active, the app remains on the home screen with a visible stop status; AI requests, speech input and live voice are blocked until the user explicitly taps the manual resume control. No identity check is required to resume.
4. Remote AI replies require a user-configured HTTPS endpoint and API key. No API key is embedded in the APK. Conversation history and local memory may be sent to the configured endpoint as part of a chat request.
5. Local notes, chat history and API key are stored using AES-GCM with an Android Keystore key. Android backup is disabled, cleartext traffic is disabled and screenshots/recents previews are blocked using FLAG_SECURE.

## HIC-STOP-01
- The manual in-app stop locks the current session, disconnects an active request, invalidates stale replies and stops speech output.
- Stop state is persisted synchronously with `commit()`. If persistence fails, the app attempts to restore the stop flag, keeps the in-memory latch engaged, and displays a warning.
- Clearing a persisted stop requires only the user's explicit tap on the same control; there is no separate confirmation dialog or identity-check step. If clearing the flag cannot be persisted, the code attempts to restore the stop flag and keeps AI/audio work blocked.
- This is an in-app software latch, not a physical switch or protection against app deletion/device compromise. The app does not change Android security settings.

## Fail-closed policy
- `OwnerVerificationGate` defaults every evidence item to false and emergency stop to active.
- The app does not require identity, liveness, face, voice-identity, or biometric evidence to open. The only access stop is the user-controlled HIC-STOP-01 latch.
- The old `SpeakerModelEngine` and `VoiceIdentityFlow` are preserved outside the Android source set. The ONNX model is missing and liveness/replay resistance are unvalidated; they must not be compiled in or used to authorize actions until properly implemented and tested.
- The app does not request unrelated audio-file access or store raw speech-recognition audio. The external speech-recognition service may process speech under its own policies.

- Signed release publishing is restricted to pushes on the `@Chat-GPT` default branch; feature branches must not create release assets.

## Release checks
- CI: JVM tests, project/security source checks, APK build, signer verification and artifact existence.
- On the actual phone: fresh installation and upgrade, first launch directly to home, microphone permission granted/denied/revoked, text-only use without microphone permission, stop persistence across restart, background/foreground transitions, AI endpoint errors, encrypted-memory persistence, speech cancellation and Persian TTS. Do not test biometric login or camera login: neither is part of the current stop-only design.
- Before public distribution, complete the checklist in `docs/ANDROID_INSTALL_RELEASE_CHECKLIST_FA.md`, verify the latest CI run, confirm the release signing certificate, and complete applicable privacy/Google Play declarations. Repository files cannot grant external approvals.
- CI success does not prove real-device installation, runtime correctness, API availability or production security.


## Background voice, cloud sync, notifications and updates

- Live audio is hosted by `HectorVoiceForegroundService` with the Android `microphone` foreground-service type and a persistent notification with a stop action. The service must be started from a visible activity after microphone and notification permissions are granted; Android does not allow apps to silently start microphone capture from the background.
- Cloud memory is optional and account-scoped. Only the memory note and conversation history are synced; API keys, endpoint and model settings are excluded. Configure Firebase for `com.hector.android`, add `app/google-services.json`, enable Email/Password authentication, create Firestore, and deploy `firestore.rules` before enabling it for users.
- Push reception uses Firebase Cloud Messaging. A configured Firebase project is required; delivery also requires a trusted sender such as a Firebase Cloud Function using server-side Firebase Admin credentials. Never place Admin credentials in the APK.
- Google Play In-App Updates requires the app to be distributed and installed by Google Play. It will not update APKs installed directly from GitHub or Termux.
- CI can compile, lint, unit-test and sign-check the APK. It cannot prove microphone/background behavior on a physical phone without a device/emulator test run.
