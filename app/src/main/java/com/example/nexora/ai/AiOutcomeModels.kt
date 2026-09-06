package com.example.nexora.ai

import java.time.Instant

enum class AiOutcomeType {
    SUCCESS,
    PARTIAL_SUCCESS,
    NOT_COMPLETED,
    CARRIED_FORWARD,
    REJECTED,
    MODIFIED,
    FAILED,
    PLAN_TOO_LARGE,
    PLAN_TOO_SMALL,
    PLAN_REALISTIC,
    UNKNOWN
}

data class AiOutcome(
    val id: String,
    val recommendationId: String?,
    val actionId: String?,
    val type: AiOutcomeType,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null,
    val expectedResult: String? = null,
    val actualResult: String? = null,
    val confidence: AiConfidence = AiConfidence.MEDIUM,
    val evidence: String? = null
)

data class AiRecommendationHistory(
    val id: String,
    val type: AiRecommendationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedTaskId: Long? = null,
    val relatedGoalId: Long? = null,
    val confidence: AiConfidence = AiConfidence.MEDIUM
)

data class AiEvaluation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val whatWasExpected: String,
    val whatActuallyHappened: String,
    val outcome: AiOutcomeType,
    val improvementSignal: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: AiConfidence = AiConfidence.MEDIUM
)

data class RecommendationEffectiveness(
    val recommendationType: AiRecommendationType,
    val successRate: Float,
    val sampleCount: Int,
    val confidence: AiConfidence
)
