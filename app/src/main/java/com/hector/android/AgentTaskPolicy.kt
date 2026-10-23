package com.hector.android

/**
 * Policy primitives for Hector's multi-step task runner.
 * No Android, network, or shell side effects live here: the caller supplies
 * the planner and executor so policy can be audited and tested independently.
 */
object AgentTaskPolicy {
    const val MAX_STEPS = 8
    const val MAX_RETRIES_PER_STEP = 2
    const val MAX_STEP_OUTPUT_CHARS = 8_000
    const val MAX_TASK_CHARS = 4_000

    enum class Risk { READ_ONLY, REVERSIBLE_WRITE, EXTERNAL_SIDE_EFFECT, PRIVILEGED }
    enum class StepState { PENDING, RUNNING, SUCCEEDED, FAILED, NEEDS_APPROVAL, SKIPPED }

    data class Step(
        val id: String,
        val tool: String,
        val description: String,
        val risk: Risk,
        val arguments: Map<String, String> = emptyMap()
    )

    data class Outcome(val state: StepState, val output: String, val attempts: Int)

    fun validateTask(task: String): String {
        val normalized = task.trim()
        require(normalized.isNotEmpty()) { "Task must not be empty." }
        require(normalized.length <= MAX_TASK_CHARS) {
            "Task exceeds the ${MAX_TASK_CHARS}-character limit."
        }
        return normalized
    }

    fun validatePlan(steps: List<Step>): List<Step> {
        require(steps.isNotEmpty()) { "The plan must contain at least one step." }
        require(steps.size <= MAX_STEPS) { "The plan exceeds the $MAX_STEPS-step limit." }
        val ids = HashSet<String>()
        for (step in steps) {
            require(step.id.matches(Regex("[A-Za-z0-9_-]{1,48}"))) { "Invalid step identifier." }
            require(ids.add(step.id)) { "Step identifiers must be unique." }
            require(step.tool.matches(Regex("[a-z][a-z0-9_]{1,47}"))) { "Invalid tool name." }
            require(step.description.isNotBlank() && step.description.length <= 240) {
                "Each step needs a short description."
            }
            require(step.arguments.size <= 24) { "Too many arguments in a step." }
            for ((key, value) in step.arguments) {
                require(key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,47}"))) { "Invalid argument name." }
                require(value.length <= 4_000) { "An argument exceeds the size limit." }
            }
        }
        return steps.toList()
    }

    /** Model output alone cannot authorize external or privileged operations. */
    fun requiresApproval(risk: Risk): Boolean =
        risk == Risk.EXTERNAL_SIDE_EFFECT || risk == Risk.PRIVILEGED

    fun boundedOutput(output: String): String =
        if (output.length <= MAX_STEP_OUTPUT_CHARS) output
        else output.take(MAX_STEP_OUTPUT_CHARS) + "\n[output truncated by Hector policy]"

    fun retryBudget(attemptsAlreadyMade: Int): Int =
        (MAX_RETRIES_PER_STEP - attemptsAlreadyMade).coerceAtLeast(0)
}
