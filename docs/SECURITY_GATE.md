# Hector security gate — face-login branch

## Login design in this build
1. Stage 1 asks Android for a strong device biometric and binds a fresh random challenge to an Android Keystore AES-GCM operation.
2. Android chooses the modality for its biometric prompt; this is not guaranteed to be fingerprint-only.
3. Stage 2 is designated for camera-based owner-face identity matching. The actual face-embedding matcher and local enrollment template are NOT implemented in this revision. The UI intentionally keeps this stage disabled and does not claim face identity has been verified.
4. Passing stage 1 never unlocks the owner area or sensitive actions. The emergency-stop latch is explicitly kept active.
5. HIC-STOP-01 remains available as a manual emergency stop.

## Voice removal
- Voice login code and UI have been removed from MainActivity.
- RECORD_AUDIO permission, speech-recognition query, sherpa-onnx dependency and model download step have been removed from the manifest/build workflow.
- This app version does not request microphone access or read unrelated audio files.

## Security defaults
- Fail closed on biometric cancellation, errors, lifecycle stop, or missing proof.
- Sensitive operations remain locked; no network features are exposed from this login scaffold.
- Android backups and cleartext HTTP are disabled; screenshots are blocked using FLAG_SECURE.
- Emergency-stop latch is never cleared by successful biometric authentication.
- No face photo is embedded in the public repository or APK.

## Not implemented / not verified
- Camera capture, face enrollment, face embeddings, similarity threshold, anti-spoofing and genuine owner-face matching.
- Device installation and runtime testing.
- Independent security review and false-accept/false-reject testing.

A CI build only establishes compilation and artifact creation. It does not establish working face recognition or device-level security. Do not represent this branch as face-authenticated until the face matching engine is integrated and tested.
