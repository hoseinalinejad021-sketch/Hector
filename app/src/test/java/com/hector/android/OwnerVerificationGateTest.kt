package com.hector.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerVerificationGateTest {
    @Test
    fun defaultEvidenceDeniesSensitiveOperations() {
        assertFalse(OwnerVerificationGate.allowsSensitiveOperation(OwnerVerificationEvidence()))
    }

    @Test
    fun allRequiredProofsAndConsentAreRequired() {
        val valid = OwnerVerificationEvidence(
            deviceBiometricPassed = true,
            faceIdentityMatched = true,
            faceLivenessPassed = true,
            speakerIdentityMatched = true,
            voiceLivenessPassed = true,
            explicitActionConsent = true,
            emergencyStopActive = false
        )
        assertTrue(OwnerVerificationGate.allowsSensitiveOperation(valid))
        assertFalse(OwnerVerificationGate.allowsSensitiveOperation(valid.copy(deviceBiometricPassed = false)))
        assertFalse(OwnerVerificationGate.allowsSensitiveOperation(valid.copy(faceIdentityMatched = false)))
        assertFalse(OwnerVerificationGate.allowsSensitiveOperation(valid.copy(faceLivenessPassed = false)))
        assertFalse(OwnerVerificationGate.allowsSensitiveOperation(valid.copy(speakerIdentityMatched = false)))
        assertFalse(OwnerVerificationGate.allowsSensitiveOperation(valid.copy(voiceLivenessPassed = false)))
        assertFalse(OwnerVerificationGate.allowsSensitiveOperation(valid.copy(explicitActionConsent = false)))
        assertFalse(OwnerVerificationGate.allowsSensitiveOperation(valid.copy(emergencyStopActive = true)))
    }
}
