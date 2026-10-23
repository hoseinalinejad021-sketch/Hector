package com.hector.android

/**
 * Explicit allowlist for local, read-only agent tools.
 *
 * This registry deliberately has no shell, network, arbitrary file, or external-action
 * capability. The model may request a tool, but only this registry can dispatch it.
 */
object AgentToolRegistry {
    const val MAX_QUERY_CHARS = 240
    const val MAX_RESULT_CHARS = 4_000

    data class ToolDefinition(
        val name: String,
        val description: String,
        val risk: AgentTaskPolicy.Risk = AgentTaskPolicy.Risk.READ_ONLY
    )

    data class Result(val ok: Boolean, val text: String)

    val definitions: List<ToolDefinition> = listOf(
        ToolDefinition("read_memory", "Read the user's locally saved Hector memory."),
        ToolDefinition("search_history", "Search locally saved conversation history for a short phrase.")
    )

    private val byName = definitions.associateBy { it.name }

    fun definition(name: String): ToolDefinition? = byName[name]

    /** Dispatches only known read-only tools; arguments are already parsed by the caller. */
    fun execute(
        name: String,
        arguments: Map<String, String>,
        memory: String,
        history: List<String>
    ): Result {
        val definition = byName[name]
            ?: return Result(false, "Unknown or disabled tool.")
        if (AgentTaskPolicy.requiresApproval(definition.risk)) {
            return Result(false, "This tool requires explicit user approval.")
        }
        return when (name) {
            "read_memory" -> {
                if (arguments.isNotEmpty()) Result(false, "read_memory takes no arguments.")
                else Result(true, bounded(memory.ifBlank { "No local memory saved." }))
            }
            "search_history" -> {
                if (arguments.keys != setOf("query")) {
                    Result(false, "search_history requires exactly one query argument.")
                } else {
                    val query = arguments["query"]?.trim().orEmpty()
                    if (query.isEmpty() || query.length > MAX_QUERY_CHARS) {
                        Result(false, "Query must contain 1 to $MAX_QUERY_CHARS characters.")
                    } else {
                        val matches = history.filter { it.contains(query, ignoreCase = true) }.take(8)
                        Result(true, bounded(if (matches.isEmpty()) "No matching history entries." else matches.joinToString("\\n---\\n")))
                    }
                }
            }
            else -> Result(false, "Tool is not executable.")
        }
    }

    fun bounded(value: String): String =
        if (value.length <= MAX_RESULT_CHARS) value
        else value.take(MAX_RESULT_CHARS) + "\\n[truncated]"
}
