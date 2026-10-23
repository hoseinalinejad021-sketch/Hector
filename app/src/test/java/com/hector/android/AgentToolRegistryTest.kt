package com.hector.android

import org.junit.Assert.*
import org.junit.Test

class AgentToolRegistryTest {
    @Test fun exposesOnlyExplicitReadOnlyTools() {
        assertNotNull(AgentToolRegistry.definition("read_memory"))
        assertNotNull(AgentToolRegistry.definition("search_history"))
        assertNull(AgentToolRegistry.definition("shell"))
        assertNull(AgentToolRegistry.definition("http_request"))
        assertTrue(AgentToolRegistry.definitions.all {
            it.risk == AgentTaskPolicy.Risk.READ_ONLY
        })
    }

    @Test fun readsMemoryWithoutAcceptingArguments() {
        val result = AgentToolRegistry.execute("read_memory", emptyMap(), "remember this", emptyList())
        assertTrue(result.ok)
        assertEquals("remember this", result.text)
        assertFalse(AgentToolRegistry.execute("read_memory", mapOf("path" to "/"), "", emptyList()).ok)
    }

    @Test fun searchesHistoryCaseInsensitivelyAndBoundsResults() {
        val result = AgentToolRegistry.execute(
            "search_history", mapOf("query" to "hector"), "",
            listOf("Hector started", "another note", "Ask HECTOR tomorrow")
        )
        assertTrue(result.ok)
        assertTrue(result.text.contains("Hector started"))
        assertTrue(result.text.contains("Ask HECTOR tomorrow"))
        assertFalse(AgentToolRegistry.execute("search_history", emptyMap(), "", emptyList()).ok)
        assertFalse(AgentToolRegistry.execute("search_history", mapOf("query" to "x".repeat(241)), "", emptyList()).ok)
        assertTrue(AgentToolRegistry.bounded("x".repeat(AgentToolRegistry.MAX_RESULT_CHARS + 10)).length < AgentToolRegistry.MAX_RESULT_CHARS + 20)
    }

    @Test fun rejectsUnknownToolsAndLimitsHistoryMatches() {
        assertFalse(AgentToolRegistry.execute("delete_everything", emptyMap(), "", emptyList()).ok)
        val history = (1..20).map { "match $it" }
        val result = AgentToolRegistry.execute("search_history", mapOf("query" to "match"), "", history)
        assertTrue(result.ok)
        assertTrue(result.text.contains("match 8"))
        assertFalse(result.text.contains("match 9"))
    }
}
