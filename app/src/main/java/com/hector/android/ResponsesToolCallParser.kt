package com.hector.android

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Strict parser for Responses API function-call output; never dispatches model text as a command. */
object ResponsesToolCallParser {
    const val MAX_CALLS_PER_RESPONSE = 4
    const val MAX_ARGUMENTS_CHARS = 2_000
    private val ALLOWED_FUNCTION_NAMES = setOf("read_memory", "search_history")

    data class Call(val callId: String, val name: String, val arguments: Map<String, String>)

    data class Parsed(val calls: List<Call>, val text: String)

    fun parse(response: JSONObject): Parsed {
        val output = response.optJSONArray("output") ?: JSONArray()
        val calls = mutableListOf<Call>()
        val text = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "function_call" -> {
                    require(calls.size < MAX_CALLS_PER_RESPONSE) { "Too many function calls in one response." }
                    val callId = item.optString("call_id")
                    val name = item.optString("name")
                    val rawArguments = item.optString("arguments")
                    require(callId.isNotBlank() && callId.length <= 200) { "Invalid function call id." }
                    require(name.matches(Regex("[a-z][a-z0-9_]{0,47}")) && name in ALLOWED_FUNCTION_NAMES) {
                        "Unsupported function name."
                    }
                    require(rawArguments.length <= MAX_ARGUMENTS_CHARS) { "Function arguments exceed the size limit." }
                    val json = try { JSONObject(rawArguments) } catch (_: JSONException) {
                        throw IllegalArgumentException("Function arguments must be a JSON object.")
                    }
                    val arguments = linkedMapOf<String, String>()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = json.opt(key)
                        require(value is String) { "Only string-valued function arguments are supported." }
                        require(key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,47}"))) { "Invalid argument name." }
                        arguments[key] = value
                    }
                    calls += Call(callId, name, arguments)
                }
                "message" -> {
                    val content = item.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val part = content.optJSONObject(j) ?: continue
                        if (part.optString("type") == "output_text") text.append(part.optString("text"))
                    }
                }
            }
        }
        return Parsed(calls, text.toString())
    }
}
