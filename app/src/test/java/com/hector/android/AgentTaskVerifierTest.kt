package com.hector.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentTaskVerifierTest {
    @Test fun successRequiresObservedExecutionAndAtLeastOnePassedCheck() {
        assertEquals(AgentTaskVerifier.Outcome.INCOMPLETE,
            AgentTaskVerifier.evaluate(AgentTaskVerifier.Evidence(false, listOf(true))).outcome)
        assertEquals(AgentTaskVerifier.Outcome.INCOMPLETE,
            AgentTaskVerifier.evaluate(AgentTaskVerifier.Evidence(true, emptyList())).outcome)
        assertEquals(AgentTaskVerifier.Outcome.VERIFIED_SUCCESS,
            AgentTaskVerifier.evaluate(AgentTaskVerifier.Evidence(true, listOf(true, true))).outcome)
    }

    @Test fun failedOrCancelledExecutionCannotBeReportedAsSuccess() {
        assertEquals(AgentTaskVerifier.Outcome.FAILED,
            AgentTaskVerifier.evaluate(AgentTaskVerifier.Evidence(true, listOf(true), executionError = "exit 1")).outcome)
        assertEquals(AgentTaskVerifier.Outcome.CANCELLED,
            AgentTaskVerifier.evaluate(AgentTaskVerifier.Evidence(true, listOf(true), cancelled = true)).outcome)
        assertEquals(AgentTaskVerifier.Outcome.INCOMPLETE,
            AgentTaskVerifier.evaluate(AgentTaskVerifier.Evidence(true, listOf(true, false))).outcome)
    }
}
