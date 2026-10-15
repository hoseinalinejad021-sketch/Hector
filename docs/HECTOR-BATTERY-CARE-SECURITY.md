# Hector Battery Care and Basic Security

Status: IMPLEMENTED IN SOURCE; CI build and Android-device behavior must be verified separately.

## Included
- Read-only battery percentage, charging/powered state, reported temperature and Android-reported battery health.
- Shows Android Battery Saver status, Android version and security-patch date when available.
- Buttons open Android battery and security settings.
- Displays practical, non-invasive battery suggestions.
- No new runtime permissions are requested. No background service, automatic task killing, system-setting changes, or private-file access is added.

## Security scope and limits
- This is a basic checklist, not an antivirus engine. Hector does not scan installed applications, detect malware, or replace Google Play Protect.
- The user should open the Play Store -> profile icon -> Play Protect -> Scan to request the device's built-in app scan.
- Android-reported battery health is not available on every device and is not a laboratory battery-capacity test.
- Battery tips are suggestions only; Hector does not automatically change device settings.
- No battery diagnostic data is uploaded or stored by this feature.

## Acceptance checks
- Build APK successfully in GitHub Actions.
- Install and test on a real Android device.
- Confirm battery status refreshes, settings buttons open correctly, and emergency-stop behavior remains unchanged.
