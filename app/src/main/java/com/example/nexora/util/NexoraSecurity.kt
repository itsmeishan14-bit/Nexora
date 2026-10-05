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

    const val PROPOSAL_TTL_MS = 15 * 60 * 1000L // 15 minutes

    enum class ProposalStatus {
        PENDING,
        CONSUMED,
        CANCELLED,
        EXPIRED
    }

    data class PendingProposal(
        val actionId: String,
        val actionType: AiActionType,
        val targetTaskId: Long?,
        val targetGoalId: Long?,
        val fingerprint: String,
        val confirmationToken: String,
        val proposedAt: Long = System.currentTimeMillis(),
        val expiresAt: Long = proposedAt + PROPOSAL_TTL_MS,
        @Volatile var status: ProposalStatus = ProposalStatus.PENDING
    )

    data class AuthorizationGrant(
        val actionId: String,
        val actionType: AiActionType,
        val targetTaskId: Long?,
        val targetGoalId: Long?,
        val fingerprint: String,
        val token: String,
        val grantedAt: Long = System.currentTimeMillis()
    )

    private val pendingProposals = java.util.concurrent.ConcurrentHashMap<String, PendingProposal>()
    private val activeGrants = java.util.concurrent.ConcurrentHashMap<String, AuthorizationGrant>()

    fun computeFingerprint(action: AiAction): String {
        val cleanParams = action.parameters.filterKeys { 
            it != "userConfirmed" && it != "authorizationToken" && it != "confirmationToken"
        }.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
        return "${action.type}:${action.taskId}:${action.goalId}:$cleanParams"
    }

    /**
     * Registers a new pending action proposal issued by a trusted application flow.
     * Generates a unique, one-time confirmation token bound to the action's type, target, and parameters.
     * Any previous proposal for this actionId is cancelled and replaced.
     */
    fun registerProposal(action: AiAction, ttlMs: Long = PROPOSAL_TTL_MS): AiAction {
        // If an existing proposal exists for this actionId, cancel it
        pendingProposals[action.id]?.let { oldProposal ->
            synchronized(oldProposal) {
                if (oldProposal.status == ProposalStatus.PENDING) {
                    oldProposal.status = ProposalStatus.CANCELLED
                }
            }
        }
        activeGrants.remove(action.id)

        val confirmationToken = java.util.UUID.randomUUID().toString()
        val fingerprint = computeFingerprint(action)
        val now = System.currentTimeMillis()
        val proposal = PendingProposal(
            actionId = action.id,
            actionType = action.type,
            targetTaskId = action.taskId,
            targetGoalId = action.goalId,
            fingerprint = fingerprint,
            confirmationToken = confirmationToken,
            proposedAt = now,
            expiresAt = now + ttlMs,
            status = ProposalStatus.PENDING
        )
        pendingProposals[action.id] = proposal

        val updatedParams = action.parameters.toMutableMap()
        updatedParams["confirmationToken"] = confirmationToken

        return action.copy(parameters = updatedParams)
    }

    /**
     * Explicitly cancels/dismisses a pending proposal so it cannot be confirmed.
     */
    fun cancelProposal(actionId: String) {
        pendingProposals[actionId]?.let { proposal ->
            synchronized(proposal) {
                if (proposal.status == ProposalStatus.PENDING) {
                    proposal.status = ProposalStatus.CANCELLED
                }
            }
        }
        revokeAuthorization(actionId)
    }

    /**
     * Checks if an action has a currently pending (unexpired, unconsumed, uncancelled) proposal.
     */
    fun isProposalPending(actionId: String): Boolean {
        val proposal = pendingProposals[actionId] ?: return false
        if (proposal.status != ProposalStatus.PENDING) return false
        if (System.currentTimeMillis() > proposal.expiresAt) {
            proposal.status = ProposalStatus.EXPIRED
            return false
        }
        return true
    }

    sealed class ConsumeResult {
        data class Success(val authorizedAction: AiAction) : ConsumeResult()
        data class Rejected(val reason: String, val error: String) : ConsumeResult()
    }

    /**
     * Validates and atomically consumes a pending proposal, granting authorization.
     * A proposal can be consumed at most ONCE.
     */
    fun consumeAndAuthorize(action: AiAction): ConsumeResult {
        val proposal = pendingProposals[action.id]
            ?: return ConsumeResult.Rejected(
                "Action was not proposed by a trusted application flow.",
                "No pending proposal"
            )

        synchronized(proposal) {
            if (proposal.status == ProposalStatus.CONSUMED) {
                return ConsumeResult.Rejected(
                    "This action proposal has already been confirmed and consumed.",
                    "Proposal already consumed"
                )
            }
            if (proposal.status == ProposalStatus.CANCELLED) {
                return ConsumeResult.Rejected(
                    "This action proposal was cancelled or dismissed.",
                    "Proposal cancelled"
                )
            }
            if (System.currentTimeMillis() > proposal.expiresAt || proposal.status == ProposalStatus.EXPIRED) {
                proposal.status = ProposalStatus.EXPIRED
                return ConsumeResult.Rejected(
                    "This action proposal has expired.",
                    "Proposal expired"
                )
            }
            if (proposal.status != ProposalStatus.PENDING) {
                return ConsumeResult.Rejected(
                    "Action proposal is not eligible for confirmation.",
                    "Proposal not pending"
                )
            }

            // 1. Verify action type matches
            if (action.type != proposal.actionType) {
                NexoraLogger.w("SECURITY", "Action type mismatch for proposal ${action.id}")
                return ConsumeResult.Rejected(
                    "Action type mismatch with pending proposal.",
                    "Action type mismatch"
                )
            }

            // 2. Verify target IDs match
            if (action.taskId != proposal.targetTaskId || action.goalId != proposal.targetGoalId) {
                NexoraLogger.w("SECURITY", "Target ID mismatch for proposal ${action.id}")
                return ConsumeResult.Rejected(
                    "Target ID mismatch with pending proposal.",
                    "Target mismatch"
                )
            }

            // 3. Verify parameters match proposal fingerprint
            val currentFingerprint = computeFingerprint(action)
            if (proposal.fingerprint != currentFingerprint) {
                NexoraLogger.w("SECURITY", "Fingerprint mismatch for proposal ${action.id}")
                return ConsumeResult.Rejected(
                    "Action parameters were modified after proposal was issued.",
                    "Parameters modified"
                )
            }

            // 4. Verify confirmation token matches
            val actionToken = action.parameters["confirmationToken"]?.toString()
            if (actionToken.isNullOrBlank() || actionToken != proposal.confirmationToken) {
                NexoraLogger.w("SECURITY", "Confirmation token mismatch for proposal ${action.id}")
                return ConsumeResult.Rejected(
                    "Invalid or missing confirmation handle for proposal.",
                    "Invalid confirmation handle"
                )
            }

            // Atomically mark CONSUMED
            proposal.status = ProposalStatus.CONSUMED

            // Issue the single-use authorization grant
            val grantToken = java.util.UUID.randomUUID().toString()
            val grant = AuthorizationGrant(
                actionId = action.id,
                actionType = action.type,
                targetTaskId = action.taskId,
                targetGoalId = action.goalId,
                fingerprint = currentFingerprint,
                token = grantToken
            )
            activeGrants[action.id] = grant

            val authorizedParams = action.parameters.toMutableMap()
            authorizedParams["userConfirmed"] = true
            authorizedParams["authorizationToken"] = grantToken

            return ConsumeResult.Success(
                action.copy(
                    requiresConfirmation = false,
                    parameters = authorizedParams
                )
            )
        }
    }

    fun revokeAuthorization(actionId: String) {
        activeGrants.remove(actionId)
    }

    fun clearAllAuthorizations() {
        activeGrants.clear()
        pendingProposals.clear()
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
