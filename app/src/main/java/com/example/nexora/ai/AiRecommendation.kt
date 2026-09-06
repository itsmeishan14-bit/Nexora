package com.example.nexora.ai

enum class AiRecommendationType {
    NEXT_TASK,
    DAILY_PLAN,
    GOAL_ACTION,
    PRODUCTIVITY_INSIGHT,
    WARNING,
    GENERAL
}

enum class AiPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class AiConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class AiRecommendation(
    val type: AiRecommendationType,
    val title: String,
    val message: String,
    val priority: AiPriority = AiPriority.MEDIUM,
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null,
    val actionLabel: String? = null,
    val evidence: String? = null,
    val confidence: AiConfidence = AiConfidence.MEDIUM
)
