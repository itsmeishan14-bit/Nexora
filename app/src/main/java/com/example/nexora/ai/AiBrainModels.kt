package com.example.nexora.ai

/**
 * Unified request for the Nexora AI Brain.
 */
data class AiRequest(
    val type: AiRequestType,
    val userMessage: String? = null,
    val taskId: Long? = null,
    val goalId: Long? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val source: String = "unknown"
)

enum class AiRequestType {
    CHAT,
    NEXT_TASK,
    DAILY_PLAN,
    GOAL_ANALYSIS,
    PRODUCTIVITY_ANALYSIS,
    GOAL_DECOMPOSITION,
    PROACTIVE_ANALYSIS,
    CREATE_TASK,
    UPDATE_TASK,
    COMPLETE_TASK,
    UPDATE_GOAL,
    GENERAL_ANALYSIS
}

/**
 * Unified response from the Nexora AI Brain.
 */
data class AiResponse(
    val responseType: AiResponseType,
    val title: String,
    val message: String,
    val confidence: AiConfidence = AiConfidence.MEDIUM,
    val evidence: List<ReasoningFactor> = emptyList(),
    val recommendations: List<AiRecommendation> = emptyList(),
    val proposedActions: List<AiAction> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null,
    val workflow: AgentWorkflow? = null
)

enum class AiResponseType {
    INFORMATION,
    RECOMMENDATION,
    ACTION_PROPOSAL,
    PLAN,
    WARNING,
    CLARIFICATION_NEEDED,
    NO_ACTION
}

/**
 * Represents a single factor in the AI reasoning process.
 */
data class ReasoningFactor(
    val factor: String,
    val impact: ReasoningImpact,
    val evidence: String
)

enum class ReasoningImpact {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
    CRITICAL
}
