package com.example.nexora.ai.evaluation

import com.example.nexora.ai.AiConfidence
import com.example.nexora.ai.AiRequestType
import com.example.nexora.ai.AiResponseType
import com.example.nexora.ai.AiActionType
import com.example.nexora.ai.AiContext
import com.example.nexora.ai.ToolRiskLevel

/**
 * A structured test case for AI evaluation.
 */
data class AiEvaluationCase(
    val caseId: String,
    val category: EvaluationCategory,
    val userInput: String,
    val requestType: AiRequestType = AiRequestType.CHAT,
    val expectedIntent: AiRequestType? = null,
    val expectedEntities: List<ExpectedEntity> = emptyList(),
    val expectedResponseType: AiResponseType? = null,
    val expectedActionType: AiActionType? = null,
    val expectedRiskLevel: ToolRiskLevel? = null,
    val testContext: AiContext? = null,
    val minConfidence: AiConfidence = AiConfidence.LOW,
    val verificationLogic: ((AiEvaluationResult) -> Boolean)? = null
)

enum class EvaluationCategory {
    INTENT_RECOGNITION,
    ENTITY_RESOLUTION,
    RECOMMENDATION_QUALITY,
    GOAL_DECOMPOSITION,
    DAILY_PLANNING,
    PROACTIVE_DETECTION,
    AGENT_RELIABILITY,
    SAFETY,
    CONVERSATIONAL_CONTINUITY,
    ADAPTIVE_BEHAVIOR,
    FALSE_POSITIVE
}

data class ExpectedEntity(
    val type: String, // "TASK", "GOAL", etc.
    val value: String? = null,
    val id: Long? = null
)

/**
 * The result of running an evaluation case.
 */
data class AiEvaluationResult(
    val caseId: String,
    val category: EvaluationCategory,
    val passed: Boolean,
    val actualIntent: AiRequestType?,
    val actualResponseType: AiResponseType?,
    val actualActionType: AiActionType?,
    val actualConfidence: AiConfidence,
    val actualMessage: String,
    val latencies: PerformanceMetrics,
    val errors: List<String> = emptyList(),
    val reasoningFactors: List<String> = emptyList()
)

data class PerformanceMetrics(
    val processingTimeMs: Long
)

/**
 * Aggregated metrics for a category or overall run.
 */
data class AiEvaluationMetric(
    val category: String,
    val totalCases: Int,
    val passedCases: Int,
    val passRate: Float,
    val averageLatencyMs: Long
)

/**
 * A comprehensive report of an evaluation run.
 */
data class AiEvaluationReport(
    val timestamp: Long = System.currentTimeMillis(),
    val overallPassRate: Float,
    val categoryMetrics: List<AiEvaluationMetric>,
    val results: List<AiEvaluationResult>
)
