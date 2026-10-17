# Hector security gate — face-login branch

## Login design in this build
1. Stage 1 asks Android for a strong device biometric and binds a fresh random challenge to an Android Keystore AES-GCM operation. Android chooses the available modality; this is not guaranteed to be fingerprint-only.
2. Stage 2 opens the system camera and runs the bundled Google ML Kit face detector on-device. The current camera intent returns a preview thumbnail, so this is a basic integration check, not a production enrollment flow.
3. ML Kit face detection finds face geometry and does NOT recognize a person. Owner-face embeddings, secure enrollment, similarity matching and calibrated thresholds are NOT implemented. Therefore, the face check never authorizes the owner or unlocks sensitive actions.
4. The emergency-stop latch remains engaged on all paths. HIC-STOP-01 remains available as a manual emergency stop.

## Voice removal
- Voice login is removed from the authentication path.
- The manifest does not request microphone permission; the app does not read unrelated audio files.

## Security defaults
- Fail closed on biometric cancellation/errors, camera cancellation, missing image, detection errors or lifecycle stop.
- Sensitive operations remain locked; no network features are exposed from this login scaffold.
- Android backups and cleartext HTTP are disabled; screenshots are blocked using FLAG_SECURE.
- No face photo is embedded in the repository or APK.

## Not implemented / not verified
- Genuine owner-face recognition, face enrollment, face embeddings, anti-spoofing or liveness checks.
- High-resolution in-app camera preview / CameraX pipeline.
- Device installation and runtime testing.
- Independent security review and false-accept/false-reject testing.

A CI build proves only compilation and artifact creation, not working face authentication or device-level security. Do not describe this branch as face-authenticated until a genuine local face matcher is integrated and tested.
