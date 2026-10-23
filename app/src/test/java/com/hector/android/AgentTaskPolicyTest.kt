package com.hector.android

import org.junit.Assert.*
import org.junit.Test

class AgentTaskPolicyTest {
    @Test fun acceptsNormalTaskAndTrimsWhitespace() {
        assertEquals("organize notes", AgentTaskPolicy.validateTask("  organize notes  "))
    }

    @Test fun rejectsEmptyAndOversizedTasks() {
        assertThrows(IllegalArgumentException::class.java) { AgentTaskPolicy.validateTask("  ") }
        assertThrows(IllegalArgumentException::class.java) {
            AgentTaskPolicy.validateTask("x".repeat(AgentTaskPolicy.MAX_TASK_CHARS + 1))
        }
    }

    @Test fun validatesBoundedPlanAndUniqueIds() {
        val valid = AgentTaskPolicy.Step("read_1", "read_file", "Read a workspace file", AgentTaskPolicy.Risk.READ_ONLY)
        assertEquals(1, AgentTaskPolicy.validatePlan(listOf(valid)).size)
        assertThrows(IllegalArgumentException::class.java) {
            AgentTaskPolicy.validatePlan(listOf(valid, valid))
        }
        assertThrows(IllegalArgumentException::class.java) {
            (1..AgentTaskPolicy.MAX_STEPS + 1).map {
                AgentTaskPolicy.Step("step_$it", "read_file", "Read file $it", AgentTaskPolicy.Risk.READ_ONLY)
            }.let(AgentTaskPolicy::validatePlan)
        }
    }

    @Test fun externalAndPrivilegedStepsRequireApproval() {
        assertFalse(AgentTaskPolicy.requiresApproval(AgentTaskPolicy.Risk.READ_ONLY))
        assertFalse(AgentTaskPolicy.requiresApproval(AgentTaskPolicy.Risk.REVERSIBLE_WRITE))
        assertTrue(AgentTaskPolicy.requiresApproval(AgentTaskPolicy.Risk.EXTERNAL_SIDE_EFFECT))
        assertTrue(AgentTaskPolicy.requiresApproval(AgentTaskPolicy.Risk.PRIVILEGED))
    }

    @Test fun retriesAndOutputAreBounded() {
        assertEquals(2, AgentTaskPolicy.retryBudget(0))
        assertEquals(1, AgentTaskPolicy.retryBudget(1))
        assertEquals(0, AgentTaskPolicy.retryBudget(2))
        val result = AgentTaskPolicy.boundedOutput("x".repeat(AgentTaskPolicy.MAX_STEP_OUTPUT_CHARS + 5))
        assertTrue(result.length < AgentTaskPolicy.MAX_STEP_OUTPUT_CHARS + 100)
        assertTrue(result.contains("output truncated"))
    }
}
