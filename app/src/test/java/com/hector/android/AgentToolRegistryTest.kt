package com.hector.android

import org.junit.Assert.*
import org.junit.Test

class AgentToolRegistryTest {
    @Test fun exposesOnlyExplicitReadOnlyTools() {
        assertNotNull(AgentToolRegistry.definition("read_memory"))
        assertNotNull(AgentToolRegistry.definition("search_memory"))
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

    @Test fun searchesMemoryCaseInsensitivelyAndBoundsResults() {
        val memory = "Favorite color: Blue\nProject: Hector\nReminder: Call tomorrow"
        val result = AgentToolRegistry.execute(
            "search_memory", mapOf("query" to "hector"), memory, emptyList()
        )
        assertTrue(result.ok)
        assertEquals("Project: Hector", result.text)
        assertEquals("No matching memory entries.", AgentToolRegistry.execute(
            "search_memory", mapOf("query" to "missing"), memory, emptyList()
        ).text)
        assertFalse(AgentToolRegistry.execute("search_memory", emptyMap(), memory, emptyList()).ok)
        assertFalse(AgentToolRegistry.execute(
            "search_memory", mapOf("query" to "x".repeat(241)), memory, emptyList()
        ).ok)
        val manyLines = (1..20).joinToString("\n") { "Hector item $it" }
        val boundedMatches = AgentToolRegistry.execute(
            "search_memory", mapOf("query" to "hector"), manyLines, emptyList()
        )
        assertTrue(boundedMatches.text.contains("Hector item 8"))
        assertFalse(boundedMatches.text.contains("Hector item 9"))
    }

    @Test fun searchesHistoryCaseInsensitivelyAndBoundsResults() {
        val result = AgentToolRegistry.execute(
            "search_history", mapOf("query" to "hector"), "",
            listOf("Hector started", "another note", "Ask HECTOR tomorrow")
        )
        assertTrue(result.ok)
        assertTrue(result.text.contains("Hector started"))
        assertTrue(result.text.contains("Ask HECTOR tomorrow"))
        assertTrue("Matches should be separated by actual newlines", result.text.contains("Hector started\n---\nAsk HECTOR tomorrow"))
        assertFalse(AgentToolRegistry.execute("search_history", emptyMap(), "", emptyList()).ok)
        assertFalse(AgentToolRegistry.execute("search_history", mapOf("query" to "x".repeat(241)), "", emptyList()).ok)
        val truncated = AgentToolRegistry.bounded("x".repeat(AgentToolRegistry.MAX_RESULT_CHARS + 10))
        assertTrue(truncated.endsWith("\n[truncated]"))
        assertTrue(truncated.length < AgentToolRegistry.MAX_RESULT_CHARS + 20)
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
