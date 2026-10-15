# Hector Local-Network Capability Roadmap

Status: PARTIALLY IMPLEMENTED — network status UI, opt-in preference, temporary invite-code primitive, local device-record registry, and AES-GCM encrypted note-envelope primitive are in source. Remote discovery, authenticated device handshake, direct LAN transport, automatic sync, and remote relay remain NOT IMPLEMENTED. No device testing has been performed.
Purpose: define a safe path toward optional local-network features for the Hector Android app without weakening owner control.

## Product direction

Hector should be useful on the owner's own devices and trusted home/local network, including when internet access is unavailable. Network features must be opt-in, visible, limited in scope, and easy to disable.

## Proposed phases

### Phase 1 — Network status and diagnostics
- Show whether the device is offline, on Wi-Fi, or using mobile data.
- Show whether local-network features are disabled or enabled.
- Do not scan the network or open a listening port by default.
- Provide a clear permission explanation before any local-network access.

### Phase 2 — Explicit device pairing
- Pair a second owner-controlled device only after a deliberate user action.
- Use a short-lived pairing code or QR code and verify both sides.
- Store device identity securely; allow the owner to revoke a paired device.
- Never accept pairing requests silently.

### Phase 3 — Encrypted local sync
- Sync only user-selected data, such as approved notes or settings.
- Encrypt data in transit and protect local secrets using Android Keystore where appropriate.
- Include conflict handling, last-sync status, and an audit record.
- Do not sync API keys, voice recordings, unrelated phone files, or private audio by default.

### Phase 4 — Local assistant relay
- Allow an explicitly paired device to send a limited request to the phone running Hector.
- Require owner-approved permissions and show the requesting device and requested action.
- Do not expose a general-purpose remote shell, arbitrary file browser, or unrestricted command executor.
- Bind services only to the intended local interface and authenticate every request.

### Phase 5 — Resilience and tests
- Test disconnect/reconnect, malformed requests, duplicate messages, revoked devices, and app restart.
- Test that network access remains off after a fresh install unless the owner enables it.
- Test that HIC-STOP-01 cancels pending work and blocks new sensitive actions.
- Keep CI tests separate from real-device validation; never claim device tests without evidence.

## Security invariants

1. HIC-STOP-01 remains owner-controlled and cannot be disabled remotely or by the app's assistant logic.
2. No network listener, discovery scan, or synchronization starts without explicit opt-in.
3. No secret is embedded in source code, APK, logs, or sync payloads.
4. Remote requests are untrusted input; they cannot change security settings, bypass identity checks, or override the emergency stop.
5. Sensitive actions remain unavailable until real identity checks are implemented and tested.
6. Every pairing, revocation, sync, and blocked action should be visible to the owner.
7. Network features must fail closed: if authentication or validation fails, do not perform the action.

## Acceptance criteria before release

- Unit tests cover pairing expiry, revocation, permission gates, and stop-state checks.
- CI build succeeds.
- Security review confirms there is no default-open listener and no arbitrary remote execution.
- Manual Android testing confirms permissions, reconnect behavior, and emergency-stop behavior.
- The release notes clearly distinguish designed, implemented, CI-tested, and device-tested capabilities.

## Current implementation status (2026-10-16)

Implemented in source:
- Phase 1: connectivity status and explicit opt-in UI; no background scan or listener is started.
- Phase 2 foundation: five-minute, six-digit invitation code and a local device-label registry with local revocation. A registry entry is explicitly marked `locally_approved_pending_remote_handshake`; it is NOT proof of a real second device's identity. Do not treat this as production pairing.
- Phase 3 foundation: a versioned encrypted envelope for selected learning notes using PBKDF2-HMAC-SHA256 (210,000 iterations) and AES-256-GCM. The passphrase is not stored. Import currently decrypts and previews only; it does not overwrite local notes. The envelope is transferred manually, not over the LAN.
- HIC-STOP-01 blocks use of these UI actions while stopped.

Not implemented yet:
- Device discovery, real remote authentication/handshake, transport encryption between two live devices, direct local-network sync, automatic conflict resolution, audit history, and local assistant relay.
- Automated crypto/expiry/revocation tests and real-device testing.

The app must continue to describe these as foundations, not completed network pairing or live network sync. Before enabling a real socket/listener, implement mutually authenticated transport, replay protection, strict message-size limits, rate limiting, revocation enforcement, and tests. Never expose arbitrary commands or filesystem access.
