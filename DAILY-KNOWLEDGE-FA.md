# Hector Daily Knowledge Layer

This feature establishes a small, automatically refreshed public-reference layer for Hector.

- A GitHub Actions workflow runs daily at 02:20 UTC and can also be started manually.
- It fetches Wikimedia/Wikipedia's public featured feed and writes a dated JSON index to `knowledge/daily.json`.
- The Android app can read that index over HTTPS. The source and update timestamp must be shown to the user.
- This is retrieval of updated public information, **not** daily retraining or self-improvement of an AI model. Facts can be incomplete or wrong; responses should retain source links.
- A genuine conversational AI still needs a model endpoint. The app must not contain a private API key in its APK. A backend or a key entered by the owner and stored securely is required; provider limits and possible costs apply.
- Do not ingest private phone data, credentials, unrelated audio, or unreviewed executable instructions. Knowledge updates must never alter authentication, permissions, or the `HIC-STOP-01` emergency-stop policy.

## Source
Wikimedia/Wikipedia featured feed API: `https://en.wikipedia.org/api/rest_v1/feed/featured/YYYY/MM/DD`
