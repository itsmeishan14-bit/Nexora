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

enum class ActionCategory {
    INFORMATIONAL,
    SUGGESTION,
    ASSISTED_ACTION,
    CONFIRMED_ACTION
}

data class AiRecommendation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AiRecommendationType,
    val title: String,
    val message: String,
    val priority: AiPriority = AiPriority.MEDIUM,
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null,
    val actionLabel: String? = null,
    val suggestedAction: AiAction? = null,
    val requiresApproval: Boolean = true,
    val actionCategory: ActionCategory = ActionCategory.ASSISTED_ACTION,
    val evidenceQuality: EvidenceQuality = EvidenceQuality.INSUFFICIENT,
    val evidence: List<ReasoningFactor> = emptyList(),
    val confidence: AiConfidence = AiConfidence.MEDIUM
)
