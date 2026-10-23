package com.hector.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ResponsesToolCallParserTest {
    @Test fun parsesFunctionCallsAndAssistantText() {
        val response = JSONObject().put("output", JSONArray()
            .put(JSONObject().put("type", "function_call").put("call_id", "call_123")
                .put("name", "search_history").put("arguments", """{"query":"hello"}"""))
            .put(JSONObject().put("type", "message").put("content", JSONArray()
                .put(JSONObject().put("type", "output_text").put("text", "Working")))))
        val parsed = ResponsesToolCallParser.parse(response)
        assertEquals("Working", parsed.text)
        assertEquals(1, parsed.calls.size)
        assertEquals("call_123", parsed.calls.single().callId)
        assertEquals("hello", parsed.calls.single().arguments["query"])
    }

    @Test fun rejectsMalformedAndNonStringArguments() {
        fun response(args: String) = JSONObject().put("output", JSONArray().put(
            JSONObject().put("type", "function_call").put("call_id", "c1")
                .put("name", "read_memory").put("arguments", args)))
        assertThrows(IllegalArgumentException::class.java) { ResponsesToolCallParser.parse(response("{bad")) }
        assertThrows(IllegalArgumentException::class.java) { ResponsesToolCallParser.parse(response("""{"count":2}""")) }
        assertThrows(IllegalArgumentException::class.java) { ResponsesToolCallParser.parse(response("x".repeat(2_001))) }
    }

    @Test fun rejectsTooManyCallsAndBadNames() {
        val output = JSONArray()
        repeat(ResponsesToolCallParser.MAX_CALLS_PER_RESPONSE + 1) { i ->
            output.put(JSONObject().put("type", "function_call").put("call_id", "c$i")
                .put("name", "read_memory").put("arguments", "{}"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResponsesToolCallParser.parse(JSONObject().put("output", output))
        }
        val bad = JSONObject().put("output", JSONArray().put(
            JSONObject().put("type", "function_call").put("call_id", "c1")
                .put("name", "run_shell").put("arguments", "{}")))
        assertThrows(IllegalArgumentException::class.java) { ResponsesToolCallParser.parse(bad) }
    }
}
