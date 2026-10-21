# Hector unified release integration audit — 2026-10-21

## Decision

This branch is a release-candidate consolidation point based on `release-audit-2026-10-21`. It intentionally does not merge every feature branch blindly. The repository contains incompatible product variants and divergent build histories; a broad merge could restore the login/lock loop that the owner reported.

## Selected release behavior

- Package: `com.hector.android`
- Version: `1.5.1-stop-only`, versionCode 25
- No fingerprint, face, voice-identity, liveness, anti-replay, or passcode login gate.
- Main screen opens directly.
- Keep `HIC-STOP-01` as an explicit emergency stop for AI/network/voice sessions; it is not a phone lock or identity check.
- Keep encrypted local storage and HTTPS-only network behavior.
- Keep user-supplied AI endpoint/model/key; never embed credentials in the APK.

## Branch conflict inventory

- `main`: older prototype, different package (`com.hector.assistant`), min SDK 26, version 0.4.1; not a safe merge base for release.
- `@Chat-GPT` and `release-audit-2026-10-21`: align on the stop-only app source and four-secret release-signing workflow. Their histories are slightly divergent, so changes should be compared by file, not merged blindly.
- `feature/background-voice-cloud-sync`: version 1.6.0 with foreground microphone service and optional Firebase cloud sync. This is a separate feature expansion; it adds permissions and setup requirements and is not included in this release candidate.
- `fix/android-lock-vs-emergency-stop` and face/fingerprint branches: version 1.4.0 with biometric/camera authentication and liveness-related flows. These conflict with the selected no-login-gate behavior and are not merged.
- Experimental speaker/voice identity branches remain excluded because identity verification/liveness is not established as release-ready.

## Release blockers not proven by repository inspection

1. GitHub does not expose secret values for inspection. The workflow checks that all four signing secrets are non-empty at run time; their configured status is not confirmed by reading source.
2. No successful release build, signed APK verification, install test, or runtime test on the owner's Android phone has been verified in this audit.
3. A successful CI build alone would not establish reliable login, installation, network configuration, or emergency-stop behavior on the device.

## Required checks before release

1. Run the guarded release workflow with the owner's explicit confirmation after verifying all four secrets exist.
2. Confirm CI completes APK signature, alignment, package/version/SDK and permission checks.
3. Install the resulting APK on the target phone and verify direct home-screen access, Persian input/output, configured AI connectivity, encrypted persistence, and that `HIC-STOP-01` stops and resumes sessions only after explicit confirmation.
4. Publish as the release APK only after all checks pass.

No secret values, passwords, API keys, or keystore contents belong in this document or in chat.
