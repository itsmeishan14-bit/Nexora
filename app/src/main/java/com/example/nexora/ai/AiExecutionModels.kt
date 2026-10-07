package com.example.nexora.ai

import java.util.UUID

/**
 * High-level execution result status for an entire AI operation or multi-action plan.
 */
enum class ExecutionOverallStatus {
    SUCCESS,
    PARTIAL,
    FAILURE,
    CANCELLED,
    REJECTED
}

/**
 * Execution status for an individual action within a plan.
 */
enum class ActionExecutionStatus {
    SUCCESS,
    FAILED,
    SKIPPED
}

/**
 * Standardized failure reason categories.
 */
object ActionFailureReason {
    const val DUPLICATE = "duplicate"
    const val DATABASE_FAILURE = "database failure"
    const val INVALID_PARAMETERS = "invalid parameters"
    const val AUTHORIZATION_FAILURE = "authorization failure"
    const val DEPENDENCY_FAILURE = "dependency failure"
    const val CANCELLED = "cancelled"
    const val PROPOSAL_EXPIRED = "proposal expired"
    const val UNKNOWN_ERROR = "unknown execution error"
}

/**
 * Classifies an error message / result message into a standardized failure reason.
 */
fun classifyFailureReason(error: String?, message: String?): String {
    val err = (error ?: "").lowercase()
    val msg = (message ?: "").lowercase()
    val combined = "$err $msg"
    return when {
        combined.contains("duplicate") || combined.contains("already exists") || combined.contains("already been executed") ->
            ActionFailureReason.DUPLICATE
        combined.contains("authoriz") || combined.contains("trusted application") || combined.contains("token") ||
            combined.contains("handle") || combined.contains("explicit user confirmation") || combined.contains("rejected") ->
            ActionFailureReason.AUTHORIZATION_FAILURE
        combined.contains("dependency") || combined.contains("prerequisite") || combined.contains("parent goal") ->
            ActionFailureReason.DEPENDENCY_FAILURE
        combined.contains("cancel") || combined.contains("dismiss") ->
            ActionFailureReason.CANCELLED
        combined.contains("expired") ->
            ActionFailureReason.PROPOSAL_EXPIRED
        combined.contains("validat") || combined.contains("invalid") || combined.contains("empty") ||
            combined.contains("missing") || combined.contains("mismatch") || combined.contains("not found") ->
            ActionFailureReason.INVALID_PARAMETERS
        combined.contains("database") || combined.contains("sql") || combined.contains("ioexception") ||
            combined.contains("persistence") || combined.contains("verification failed") || combined.contains("failed to persist") ->
            ActionFailureReason.DATABASE_FAILURE
        else ->
            ActionFailureReason.UNKNOWN_ERROR
    }
}

/**
 * Individual action execution record preserving child action details and execution order.
 */
data class AiActionExecutionRecord(
    val id: String = UUID.randomUUID().toString(),
    val executionRecordId: String,
    val actionId: String,
    val executionOrder: Int,
    val actionType: AiActionType,
    val actionTitle: String,
    val status: ActionExecutionStatus,
    val affectedTaskId: Long? = null,
    val affectedGoalId: Long? = null,
    val message: String,
    val error: String? = null,
    val failureReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Execution record preserving the entire AI action or multi-action plan lifecycle.
 * Fully observable and transparent to the user.
 */
data class AiExecutionRecord(
    val id: String = UUID.randomUUID().toString(),
    val userPrompt: String? = null,
    val detectedIntent: String? = null,
    val overallStatus: ExecutionOverallStatus,
    val confirmationRequired: Boolean = true,
    val userConfirmed: Boolean? = null,
    val totalProposedActions: Int = 0,
    val executedActionCount: Int = 0,
    val successActionCount: Int = 0,
    val failedActionCount: Int = 0,
    val skippedActionCount: Int = 0,
    val summaryMessage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val actionExecutions: List<AiActionExecutionRecord> = emptyList()
) {
    val isCompleteSuccess: Boolean get() = overallStatus == ExecutionOverallStatus.SUCCESS
    val isPartial: Boolean get() = overallStatus == ExecutionOverallStatus.PARTIAL
    val isFailure: Boolean get() = overallStatus == ExecutionOverallStatus.FAILURE
    val isCancelled: Boolean get() = overallStatus == ExecutionOverallStatus.CANCELLED
    val isRejected: Boolean get() = overallStatus == ExecutionOverallStatus.REJECTED

    /**
     * Formats an explainable summary of the plan execution.
     */
    fun getExplainableLines(): List<String> {
        return actionExecutions.sortedBy { it.executionOrder }.map { action ->
            val prefix = when (action.status) {
                ActionExecutionStatus.SUCCESS -> "✓"
                ActionExecutionStatus.FAILED -> "✕"
                ActionExecutionStatus.SKIPPED -> "↷"
            }
            val reason = when {
                action.status == ActionExecutionStatus.SKIPPED -> " — ${action.error ?: "prerequisite failed"}"
                action.status == ActionExecutionStatus.FAILED -> " — ${action.error ?: action.failureReason ?: action.message}"
                else -> ""
            }
            "$prefix ${action.actionTitle}$reason"
        }
    }
}
