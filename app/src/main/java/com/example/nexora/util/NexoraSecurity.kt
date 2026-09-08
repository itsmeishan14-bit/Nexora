package com.example.nexora.util

import com.example.nexora.ai.AiAction
import com.example.nexora.ai.AiActionType
import com.example.nexora.ai.ToolRiskLevel

/**
 * Central security and data governance utility for Nexora.
 */
object NexoraSecurity {

    /**
     * Determines if an action is destructive and requires high-level confirmation.
     */
    fun isDestructiveAction(action: AiAction): Boolean {
        return action.type == AiActionType.DELETE_TASK || 
               action.type == AiActionType.DELETE_GOAL
    }

    /**
     * Maps an action type to its inherent risk level.
     */
    fun getRiskLevel(type: AiActionType): ToolRiskLevel {
        return when (type) {
            AiActionType.DELETE_TASK, 
            AiActionType.DELETE_GOAL -> ToolRiskLevel.DESTRUCTIVE
            
            AiActionType.CREATE_TASK, 
            AiActionType.UPDATE_TASK, 
            AiActionType.COMPLETE_TASK,
            AiActionType.RESCHEDULE_TASK,
            AiActionType.CREATE_GOAL, 
            AiActionType.UPDATE_GOAL,
            AiActionType.DECOMPOSE_GOAL -> ToolRiskLevel.LOW_RISK
            
            else -> ToolRiskLevel.SAFE
        }
    }

    /**
     * Validates that an action has the required authorization (e.g. user confirmation).
     */
    fun isAuthorized(action: AiAction): Boolean {
        val risk = getRiskLevel(action.type)
        return when (risk) {
            ToolRiskLevel.DESTRUCTIVE -> action.requiresConfirmation
            ToolRiskLevel.HIGH_RISK -> action.requiresConfirmation
            else -> true
        }
    }
}
