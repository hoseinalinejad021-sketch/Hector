package com.hector.android.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerVerificationGateTest {
    private val complete = OwnerVerificationEvidence(
        deviceBiometricPassed = true,
        faceIdentityMatched = true,
        faceLivenessPassed = true,
        speakerIdentityMatched = true,
        voiceLivenessPassed = true,
        explicitActionConsent = true,
        emergencyStopEngaged = false
    )

    @Test fun deniesByDefault() {
        assertFalse(OwnerVerificationGate.mayAuthorizeSensitiveOperation(OwnerVerificationEvidence()))
    }

    @Test fun allowsOnlyWhenEveryFactorAndConsentPass() {
        assertTrue(OwnerVerificationGate.mayAuthorizeSensitiveOperation(complete))
    }

    @Test fun deniesIfAnyFactorIsMissing() {
        assertFalse(OwnerVerificationGate.mayAuthorizeSensitiveOperation(complete.copy(faceIdentityMatched = false)))
        assertFalse(OwnerVerificationGate.mayAuthorizeSensitiveOperation(complete.copy(speakerIdentityMatched = false)))
        assertFalse(OwnerVerificationGate.mayAuthorizeSensitiveOperation(complete.copy(voiceLivenessPassed = false)))
        assertFalse(OwnerVerificationGate.mayAuthorizeSensitiveOperation(complete.copy(explicitActionConsent = false)))
    }

    @Test fun emergencyStopAlwaysDenies() {
        assertFalse(OwnerVerificationGate.mayAuthorizeSensitiveOperation(complete.copy(emergencyStopEngaged = true)))
    }
}
