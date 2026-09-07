package com.example.nexora.ai

import java.util.UUID

/**
 * Represents a controlled multi-step execution path for the AI Agent.
 */
data class AgentWorkflow(
    val id: String = UUID.randomUUID().toString(),
    val objective: String,
    val status: WorkflowStatus = WorkflowStatus.PLANNING,
    val currentStepIndex: Int = 0,
    val steps: List<AgentWorkflowStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val confidence: AiConfidence = AiConfidence.MEDIUM,
    val requiresConfirmation: Boolean = false,
    val failureReason: String? = null,
    val completionReason: String? = null
)

enum class WorkflowStatus {
    PLANNING,
    WAITING_FOR_CONFIRMATION,
    EXECUTING,
    OBSERVING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    NO_ACTION
}

data class AgentWorkflowStep(
    val id: String = UUID.randomUUID().toString(),
    val workflowId: String,
    val order: Int,
    val description: String,
    val toolName: String,
    val parameters: Map<String, Any> = emptyMap(),
    val status: StepStatus = StepStatus.PENDING,
    val confidence: AiConfidence = AiConfidence.MEDIUM,
    val reason: String? = null,
    val result: ToolResult? = null,
    val requiresConfirmation: Boolean = false
)

/**
 * Enhanced Agent Response that includes workflow details.
 */
data class AgentWorkflowResponse(
    val workflow: AgentWorkflow,
    val message: String,
    val status: WorkflowStatus,
    val executedActions: List<String> = emptyList(),
    val proposedActions: List<AiAction> = emptyList(),
    val clarificationQuestion: String? = null,
    val warnings: List<String> = emptyList()
)
