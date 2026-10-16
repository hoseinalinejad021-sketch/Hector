# Hector security gate and release checklist

## Required unlock order
1. Android strong biometric prompt. The operating system verifies the enrolled biometric; Hector never reads fingerprint templates. Android may choose another supported strong biometric modality, so the app cannot guarantee that every phone displays a fingerprint-only prompt.
2. Owner-specific voice verification using a live challenge plus replay/liveness checks.
3. Only after both checks pass may protected network features be enabled.

## Fail-closed rules
- Any cancellation, mismatch, missing hardware, timeout, app backgrounding, or process restart returns to the locked state.
- HIC-STOP-01 keeps protected features locked until the full identity flow is genuinely implemented and passed.
- No unrelated audio-file access. No microphone permission is requested in this build.
- Do not use speech-to-text or a spoken passphrase as speaker identity; either can be replayed.
- Until a tested speaker-verification engine is integrated, the second gate remains locked. No network capability may be advertised as unlocked.
- Security settings must not be changed automatically by the app.
- Android app backups are disabled; cleartext HTTP traffic is disabled; screenshots/recents previews are blocked by FLAG_SECURE.
- The manifest intentionally does not request INTERNET or RECORD_AUDIO permissions in this build.

## Implemented in this build
- AndroidX BiometricPrompt using BIOMETRIC_STRONG.
- Locked two-stage screen and emergency-stop button.
- Reset of temporary biometric state when the app leaves the foreground.
- Screenshot/recents-preview protection, backups disabled, and cleartext traffic disabled.

## Not yet implemented or verified
- Owner-specific voice enrollment and speaker verification.
- Liveness and replay-attack detection.
- Encrypted memory and any network integration.
- Physical-device testing, including restart, biometric failure, cancellation, and emergency-stop regression tests.

The debug APK can be built in GitHub Actions. A successful build only confirms compilation and artifact creation; it does not replace security review or on-device testing.
