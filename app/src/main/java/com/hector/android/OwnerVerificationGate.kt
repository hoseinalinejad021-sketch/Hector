package com.hector.android

/**
 * Fail-closed policy for any future sensitive action.
 *
 * Current camera movement and randomized speech challenges do not set faceIdentityMatched,
 * speakerIdentityMatched, or voiceLivenessPassed. Therefore this gate denies sensitive
 * actions until independently validated identity and anti-spoof implementations are added.
 */
internal data class OwnerVerificationEvidence(
    val deviceBiometricPassed: Boolean = false,
    val faceIdentityMatched: Boolean = false,
    val faceLivenessPassed: Boolean = false,
    val speakerIdentityMatched: Boolean = false,
    val voiceLivenessPassed: Boolean = false,
    val explicitActionConsent: Boolean = false,
    val emergencyStopActive: Boolean = true
)

internal object OwnerVerificationGate {
    fun allowsSensitiveOperation(evidence: OwnerVerificationEvidence): Boolean =
        evidence.deviceBiometricPassed &&
            evidence.faceIdentityMatched &&
            evidence.faceLivenessPassed &&
            evidence.speakerIdentityMatched &&
            evidence.voiceLivenessPassed &&
            evidence.explicitActionConsent &&
            !evidence.emergencyStopActive

    fun missingRequirements(evidence: OwnerVerificationEvidence): List<String> {
        val missing = mutableListOf<String>()
        if (!evidence.deviceBiometricPassed) missing += "device_biometric"
        if (!evidence.faceIdentityMatched) missing += "face_identity_match"
        if (!evidence.faceLivenessPassed) missing += "face_liveness"
        if (!evidence.speakerIdentityMatched) missing += "speaker_identity_match"
        if (!evidence.voiceLivenessPassed) missing += "voice_liveness"
        if (!evidence.explicitActionConsent) missing += "explicit_action_consent"
        if (evidence.emergencyStopActive) missing += "emergency_stop_active"
        return missing
    }
}
