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

data class AiRecommendation(
    val type: AiRecommendationType,
    val title: String,
    val message: String,
    val priority: AiPriority = AiPriority.MEDIUM,
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null,
    val actionLabel: String? = null
)