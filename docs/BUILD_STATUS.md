# Hector 1.3.1 build status

- Target version: 1.3.1-security-gate (versionCode 5).
- CI workflow: `.github/workflows/android.yml`; a push starts the debug APK build and uploads `Hector-debug-APK`.
- The voice button only requests microphone permission. Owner-specific voice identity verification is not implemented; do not treat voice as an authentication factor.
- The existing installed APK was signed with an Android debug certificate. The matching private signing key is unavailable, so this build cannot be assumed to update the installed app in place. Do not uninstall the installed copy unless replacement and possible local-data loss are accepted.
- Build success and phone installation remain unverified until the workflow completes and the APK is tested on-device.
