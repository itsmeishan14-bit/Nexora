package com.example.nexora.ai

enum class AiDecisionType {
    START_TASK,
    COMPLETE_TASK,
    RESCHEDULE_TASK,
    CREATE_TASK,
    UPDATE_TASK,
    DELETE_TASK,
    UPDATE_GOAL,
    DELETE_GOAL,
    CREATE_GOAL,
    DECOMPOSE_GOAL,
    DAILY_PLAN,
    SHOW_INSIGHT,
    WARNING,
    AMBIGUOUS,
    CLARIFY,
    NO_ACTION
}

data class AiDecision(
    val type: AiDecisionType,
    val title: String,
    val reason: String,
    val evidence: String? = null,
    val confidence: AiConfidence = AiConfidence.MEDIUM,
    val taskId: Long? = null,
    val goalId: Long? = null,
    val taskTitle: String? = null,
    val priority: AiPriority = AiPriority.MEDIUM,
    val actionLabel: String? = null
)