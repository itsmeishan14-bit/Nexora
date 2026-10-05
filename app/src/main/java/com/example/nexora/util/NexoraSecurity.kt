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
               action.type == AiActionType.DELETE_GOAL ||
               action.type == AiActionType.DELETE_ALL_TASKS ||
               action.type == AiActionType.DELETE_AUTOMATION
    }

    /**
     * Maps an action type to its inherent risk level.
     */
    fun getRiskLevel(type: AiActionType): ToolRiskLevel {
        return when (type) {
            AiActionType.DELETE_TASK, 
            AiActionType.DELETE_GOAL,
            AiActionType.DELETE_ALL_TASKS,
            AiActionType.DELETE_AUTOMATION -> ToolRiskLevel.DESTRUCTIVE
            
            AiActionType.CREATE_TASK, 
            AiActionType.UPDATE_TASK, 
            AiActionType.COMPLETE_TASK,
            AiActionType.RESCHEDULE_TASK,
            AiActionType.CREATE_GOAL, 
            AiActionType.UPDATE_GOAL,
            AiActionType.DECOMPOSE_GOAL,
            AiActionType.COMPLETE_ALL_TASKS,
            AiActionType.CREATE_AUTOMATION,
            AiActionType.TOGGLE_AUTOMATION,
            AiActionType.UPDATE_AUTOMATION -> ToolRiskLevel.LOW_RISK
            
            else -> ToolRiskLevel.SAFE
        }
    }

    data class AuthorizationGrant(
        val actionId: String,
        val actionType: AiActionType,
        val targetTaskId: Long?,
        val targetGoalId: Long?,
        val fingerprint: String,
        val token: String,
        val grantedAt: Long = System.currentTimeMillis()
    )

    private val activeGrants = java.util.concurrent.ConcurrentHashMap<String, AuthorizationGrant>()

    fun computeFingerprint(action: AiAction): String {
        val cleanParams = action.parameters.filterKeys { 
            it != "userConfirmed" && it != "authorizationToken" 
        }.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
        return "${action.type}:${action.taskId}:${action.goalId}:$cleanParams"
    }

    /**
     * Authorizes an exact pending action proposal.
     * Generates a unique authorization token bound to the action's type, target, and parameters.
     */
    fun grantAuthorization(action: AiAction): AiAction {
        val fingerprint = computeFingerprint(action)
        val token = java.util.UUID.randomUUID().toString()
        val grant = AuthorizationGrant(
            actionId = action.id,
            actionType = action.type,
            targetTaskId = action.taskId,
            targetGoalId = action.goalId,
            fingerprint = fingerprint,
            token = token
        )
        activeGrants[action.id] = grant

        val authorizedParams = action.parameters.toMutableMap()
        authorizedParams["userConfirmed"] = true
        authorizedParams["authorizationToken"] = token

        return action.copy(
            requiresConfirmation = false,
            parameters = authorizedParams
        )
    }

    fun authorize(action: AiAction): AiAction = grantAuthorization(action)

    fun revokeAuthorization(actionId: String) {
        activeGrants.remove(actionId)
    }

    fun clearAllAuthorizations() {
        activeGrants.clear()
    }

    /**
     * Validates that an action has authentic authorization.
     * Confirmation authorizes only the exact pending action (type, target ID, and parameters).
     * An action cannot bypass authorization merely because an arbitrary caller sets userConfirmed = true.
     */
    fun isAuthorized(action: AiAction): Boolean {
        val risk = getRiskLevel(action.type)
        if (risk == ToolRiskLevel.SAFE) return true

        // Check if an explicit authorization grant was registered for this action
        val grant = activeGrants[action.id]
        if (grant != null) {
            // 1. Verify action type matches
            if (action.type != grant.actionType) {
                NexoraLogger.w("SECURITY", "Authorization type mismatch for action ${action.id}")
                return false
            }

            // 2. Verify target IDs match (confirmation for one target cannot authorize another target)
            if (action.taskId != grant.targetTaskId || action.goalId != grant.targetGoalId) {
                NexoraLogger.w("SECURITY", "Authorization target mismatch for action ${action.id}")
                return false
            }

            // 3. Verify parameters match (editing an action after confirmation invalidates that confirmation)
            val currentFingerprint = computeFingerprint(action)
            if (grant.fingerprint != currentFingerprint) {
                NexoraLogger.w("SECURITY", "Authorization parameter fingerprint mismatch for action ${action.id}")
                return false
            }

            // 4. Verify authentic authorization token
            val actionToken = action.parameters["authorizationToken"]?.toString()
            if (actionToken != grant.token) {
                NexoraLogger.w("SECURITY", "Authorization token mismatch for action ${action.id}")
                return false
            }

            return true
        }

        // Low-risk actions that explicitly do not require confirmation (e.g. read-only planner tools)
        if (!action.requiresConfirmation && !isDestructiveAction(action)) {
            return true
        }

        // Destructive actions or actions requiring confirmation cannot bypass authorization merely by setting userConfirmed = true
        return false
    }
}
