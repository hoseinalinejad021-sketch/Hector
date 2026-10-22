# Hector Android architecture — 1.6.0-background-voice

## Package and release compatibility
- Application ID / namespace: `com.hector.android`.
- Current source version: `1.6.0-background-voice`, versionCode `26`.
- Compile SDK / target SDK: 36; minimum SDK: 23; Java/Kotlin bytecode target: 17.
- An in-place Android update requires the same application ID and signing certificate. Keep the personal release keystore and its passwords private; never commit them.
- A successful debug build is not a release-signed APK and does not prove the release-signing configuration is valid.

## Active Android components
- `MainActivity.kt`: chat UI, user-configured HTTPS AI endpoint, Persian speech input, text-to-speech, encrypted local memory/history, optional Firebase sign-in/sync, push registration, Play in-app update checks, and HIC-STOP-01.
- `SecureMemoryStore.kt`: AES/GCM encryption using an Android Keystore key; ciphertext and nonce are stored in app-private preferences.
- `CloudMemorySync.kt`: optional UID-scoped Firebase Authentication/Firestore sync for memory and history; AI keys and endpoint/model settings are excluded.
- `HectorFirebaseMessagingService.kt`: receives FCM messages and displays notifications. Sending push messages requires a trusted server or Cloud Function using Firebase Admin.
- `HectorVoiceForegroundService.kt` and `RealtimeVoiceSession.kt`: user-started microphone foreground service and direct Realtime WebSocket session. Availability depends on microphone permission, Android foreground-service policy, network access, and provider account/API access.
- `CodeStudioActivity.kt`: local text/code editor; confines files to its private workspace and enforces a 1 MB file limit. It does not execute saved code.
- `LiveVoiceActivity.kt`: WebView-based gateway client, if launched by an explicit intent; its URL and permission-origin checks must be kept strict.

## Identity and emergency-stop boundary
- The current design does not use fingerprint, face recognition, camera liveness, voice identity, or Android BiometricPrompt as an access gate.
- HIC-STOP-01 is a user-controlled software latch. It cancels activity-owned requests, stops speech and the voice service, and blocks new AI/voice actions while engaged. It is not a hardware kill switch and cannot protect against a compromised device or modified app.
- The app sets `FLAG_SECURE`, disables Android backup, and disables cleartext network traffic in the manifest.
- Microphone access is requested for voice features; unrelated audio-file access is not required. The foreground service must remain visibly notified while recording.
- The archived `legacy/voice_identity_prototype` sources are outside the Android source set and must not be used to authorize sensitive actions without a separately reviewed, tested implementation.

## Network and data boundaries
- AI endpoints must use HTTPS; redirects are disabled for the direct API request, and credential-bearing URLs are rejected.
- The AI API key is entered by the user and stored in the encrypted local store; it must never be embedded in source or committed to the repository.
- Chat requests may send local memory and conversation history to the configured AI provider. Users should configure only a provider they trust.
- Firebase cloud sync is optional and requires valid project configuration, Email/Password authentication, deployed Firestore rules, and device testing. Firebase configuration alone does not prove that auth, sync, or FCM delivery works.
- The Cloudflare Worker gateway keeps the provider API key server-side. Both `/live/session` and `/chat` must require `HECTOR_GATE_TOKEN`; the simple cache-based per-IP rate limit is best-effort, not a durable quota system.

## CI and release evidence
- CI source checks, unit tests, APK assembly, signature verification, and artifact upload are separate checks; none substitutes for the others.
- Instrumentation tests must actually be run on an emulator/device before claiming runtime validation. If a workflow only compiles those tests, report that distinction.
- Before release, verify the exact signing certificate, store password, alias, private-key password, package metadata, permissions, Firebase integration, and upgrade compatibility.
- Test on a real device: first install and update, microphone permission granted/denied/revoked, emergency-stop persistence and manual recovery, background/foreground transitions, API failures, encrypted-memory persistence, Persian TTS, cloud sync, push notifications, and voice-session termination.
- Repository edits and commits do not mean a Worker was deployed, an APK was built, or a phone test passed. Report each state independently.
