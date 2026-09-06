package com.example.nexora.ai

import com.example.nexora.data.NexoraRepository

/**
 * Centralized decision gate for AI reliability.
 * Validates confidence, risk, and evidence before permitting actions.
 */
class AiDecisionGate(
    private val repository: NexoraRepository
) {
    /**
     * Evaluates whether an action is safe to proceed.
     */
    fun evaluateAction(
        action: AiAction,
        confidence: AiConfidence,
        evidenceScore: Float
    ): ReliabilityResult {
        val risk = mapActionToRisk(action.type)
        
        // 1. Threshold Check
        val permitResult = checkThresholds(confidence, risk, evidenceScore)
        if (!permitResult.success) return permitResult

        // 2. Safety/Confirmation Check
        if (risk == ToolRiskLevel.HIGH_RISK || risk == ToolRiskLevel.DESTRUCTIVE) {
            if (!action.requiresConfirmation) {
                 return ReliabilityResult(
                    success = false,
                    status = ToolResultStatus.NEEDS_CONFIRMATION,
                    message = "Potentially risky action requires explicit confirmation.",
                    evaluation = buildEvaluation(action, confidence, evidenceScore, risk, failure = AiFailureType.VALIDATION_FAILURE)
                )
            }
        }

        return ReliabilityResult(
            success = true,
            status = ToolResultStatus.SUCCESS,
            message = "Action permitted by decision gate.",
            evaluation = buildEvaluation(action, confidence, evidenceScore, risk)
        )
    }

    private fun checkThresholds(confidence: AiConfidence, risk: ToolRiskLevel, evidenceScore: Float): ReliabilityResult {
        // Higher risk requires higher confidence
        val minConfidence = when (risk) {
            ToolRiskLevel.SAFE -> AiConfidence.LOW
            ToolRiskLevel.LOW_RISK -> AiConfidence.MEDIUM
            ToolRiskLevel.HIGH_RISK -> AiConfidence.HIGH
            ToolRiskLevel.DESTRUCTIVE -> AiConfidence.HIGH
        }

        if (confidence < minConfidence) {
            return ReliabilityResult(
                success = false,
                status = ToolResultStatus.FAILED,
                message = "Insufficient confidence ($confidence) for action risk level ($risk).",
                evaluation = buildEvaluation(null, confidence, evidenceScore, risk, failure = AiFailureType.CONFIDENCE_FAILURE)
            )
        }

        if (evidenceScore < 0.4f && confidence > AiConfidence.LOW) {
             // Inconsistent: High confidence with low evidence
             return ReliabilityResult(
                success = false,
                status = ToolResultStatus.FAILED,
                message = "Confidence/Evidence calibration mismatch.",
                evaluation = buildEvaluation(null, confidence, evidenceScore, risk, failure = AiFailureType.CONFIDENCE_FAILURE)
            )
        }

        return ReliabilityResult(true, ToolResultStatus.SUCCESS, "", buildEvaluation(null, confidence, evidenceScore, risk))
    }

    private fun mapActionToRisk(type: AiActionType): ToolRiskLevel {
        return when (type) {
            AiActionType.DELETE_TASK, AiActionType.DELETE_GOAL -> ToolRiskLevel.DESTRUCTIVE
            AiActionType.CREATE_TASK, AiActionType.UPDATE_TASK, AiActionType.COMPLETE_TASK,
            AiActionType.CREATE_GOAL, AiActionType.UPDATE_GOAL -> ToolRiskLevel.LOW_RISK
            else -> ToolRiskLevel.SAFE
        }
    }

    private fun buildEvaluation(
        action: AiAction?,
        confidence: AiConfidence,
        evidenceScore: Float,
        risk: ToolRiskLevel,
        failure: AiFailureType? = null
    ): AiReliabilityEvaluation {
        return AiReliabilityEvaluation(
            decisionType = action?.type?.name ?: "GENERAL",
            confidence = confidence,
            evidenceScore = evidenceScore,
            riskLevel = risk,
            failureType = failure,
            evidence = "Evaluated at decision gate with risk $risk"
        )
    }
}
