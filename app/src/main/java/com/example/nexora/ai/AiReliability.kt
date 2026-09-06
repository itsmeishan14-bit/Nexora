package com.example.nexora.ai

import java.util.UUID

/**
 * Detailed reliability evaluation for a specific AI decision or action.
 */
data class AiReliabilityEvaluation(
    val id: String = UUID.randomUUID().toString(),
    val requestId: String? = null,
    val decisionType: String,
    val confidence: AiConfidence,
    val evidenceScore: Float, // 0.0 to 1.0
    val riskLevel: ToolRiskLevel,
    val resolutionStatus: EntityResolutionStatus = EntityResolutionStatus.NONE,
    val failureType: AiFailureType? = null,
    val executionSuccess: Boolean = false,
    val verificationSuccess: Boolean = false,
    val outcomeType: AiOutcomeType = AiOutcomeType.UNKNOWN,
    val timestamp: Long = System.currentTimeMillis(),
    val evidence: String = ""
)

enum class EntityResolutionStatus {
    NONE, EXACT_MATCH, PARTIAL_MATCH, AMBIGUOUS, NOT_FOUND
}

enum class AiFailureType {
    UNDERSTANDING_FAILURE,
    ENTITY_FAILURE,
    VALIDATION_FAILURE,
    TOOL_FAILURE,
    DATABASE_FAILURE,
    VERIFICATION_FAILURE,
    CONFIDENCE_FAILURE,
    TIMEOUT,
    UNKNOWN_FAILURE
}

/**
 * Extended Tool Result for reliability tracking.
 */
enum class ToolResultStatus {
    SUCCESS,
    FAILED,
    NOT_FOUND,
    INVALID_INPUT,
    PERMISSION_REQUIRED,
    NEEDS_CONFIRMATION,
    ALREADY_COMPLETED,
    ALREADY_EXISTS,
    VERIFICATION_FAILED
}

data class ReliabilityResult(
    val success: Boolean,
    val status: ToolResultStatus,
    val message: String,
    val evaluation: AiReliabilityEvaluation
)
