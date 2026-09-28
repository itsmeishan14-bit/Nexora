package com.example.nexora.ai

import java.util.UUID

/**
 * Structured model representing a predictive personal intelligence output.
 */
data class AiPrediction(
    val id: String = UUID.randomUUID().toString(),
    val type: PredictionType,
    val targetId: Long? = null,
    val targetTitle: String? = null,
    val prediction: String,
    val probability: Float = 0.0f,
    val confidence: AiConfidence = AiConfidence.LOW,
    val riskLevel: AiPriority = AiPriority.LOW,
    val evidence: String = "",
    val contributingFactors: List<ReasoningFactor> = emptyList(),
    val estimatedDaysToCompletion: Int? = null,
    val generatedAt: Long = System.currentTimeMillis()
)

enum class PredictionType {
    TASK_DELAY_RISK,
    TASK_COMPLETION_LIKELIHOOD,
    GOAL_RISK,
    GOAL_COMPLETION_TIMING,
    WORKLOAD_OVERLOAD_RISK,
    PRODUCTIVITY_TREND_PREDICTION
}
