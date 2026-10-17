package com.hector.android.security

/**
 * Single fail-closed policy for authorizing sensitive actions.
 *
 * These flags must be produced by real, wired and validated verification
 * components. UI events, face detection, speech transcription, or placeholder
 * embeddings must never be used to set them to true.
 */
data class OwnerVerificationEvidence(
    val deviceBiometricPassed: Boolean = false,
    val faceIdentityMatched: Boolean = false,
    val faceLivenessPassed: Boolean = false,
    val speakerIdentityMatched: Boolean = false,
    val voiceLivenessPassed: Boolean = false,
    val explicitActionConsent: Boolean = false,
    val emergencyStopEngaged: Boolean = true
)

object OwnerVerificationGate {
    fun mayAuthorizeSensitiveOperation(evidence: OwnerVerificationEvidence): Boolean =
        evidence.deviceBiometricPassed &&
            evidence.faceIdentityMatched &&
            evidence.faceLivenessPassed &&
            evidence.speakerIdentityMatched &&
            evidence.voiceLivenessPassed &&
            evidence.explicitActionConsent &&
            !evidence.emergencyStopEngaged
}
