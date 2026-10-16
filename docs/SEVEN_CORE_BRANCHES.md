# Hector — Seven Core Branches

This file records the seven planned capability branches for the private Hector Android project. It is a roadmap, not a claim that all features are implemented.

## 1. Secure Core and Owner Gate
Protected actions must fail closed unless the required owner checks succeed. Android strong biometrics can be used through BiometricPrompt. Owner-specific voice verification and voice liveness remain unimplemented until a vetted on-device implementation is integrated and tested.

## 2. HIC-STOP-01 Emergency Stop
The stop defaults to engaged. Protected actions remain blocked while it is engaged or its state is uncertain. Hector must not disable or rewrite its own stop policy. This is a software latch, not an independent physical control.

## 3. Encrypted Memory
Store only user-approved notes and settings. Encrypt sensitive local data using Android Keystore-backed keys. Add safe backup/restore, versioned migrations, and tests for restart and corrupted data. Never silently delete or overwrite user data.

## 4. Network Health
Provide bounded, user-triggered connectivity, reachability, and latency checks; detect repeated failures and show practical suggestions. Do not claim to boost carrier signal or bypass provider limits. Avoid unnecessary background scanning.

## 5. Battery and Performance
Report Hector's own background activity and offer user-approved reductions in nonessential work when battery is low. Do not promise to repair battery health or change Android security settings automatically.

## 6. Diagnostics and Recovery
Show app/build version, permission state, storage health, and self-check results. Keep logs local and redact secrets. Any recovery that changes user data must be transparent, reversible where possible, and approved by the owner.

## 7. Modular Design and Regression Tests
Keep security, stop control, memory, network checks, battery guidance, and diagnostics in separately testable modules with least-privilege permissions. Add tests for denied permissions, offline operation, process death, restart persistence, emergency stop, and failure-closed identity checks.

## Build gate
Before describing this roadmap as implemented, confirm the Android project root and Gradle wrapper, repair the GitHub Actions build workflow, build a debug APK, verify the uploaded artifact, and test the APK on the owner's phone. Do not enable financial-account access or trade execution in this prototype.
