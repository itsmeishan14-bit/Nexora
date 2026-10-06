package com.example.nexora.ai

data class AiConversationalState(
    val pendingAction: AiAction? = null,
    val pendingPlan: List<AiAction> = emptyList(),
    val candidateTaskIds: List<Long> = emptyList(),
    val candidateGoalIds: List<Long> = emptyList(),
    val missingField: String? = null,
    val lastTaskId: Long? = null,
    val lastGoalId: Long? = null,
    val lastEntityTitle: String? = null
)

fun AiConversationalState.toConversationContext(): AiConversationContext {
    val plan = if (this.pendingPlan.isNotEmpty()) this.pendingPlan else listOfNotNull(this.pendingAction)
    return AiConversationContext(
        pendingAction = plan.firstOrNull(),
        pendingPlan = plan,
        candidateIds = this.candidateTaskIds,
        lastTaskId = this.lastTaskId,
        lastGoalId = this.lastGoalId,
        lastEntityTitle = this.lastEntityTitle
    )
}

fun AiConversationContext.toAiConversationalState(): AiConversationalState {
    val plan = if (this.pendingPlan.isNotEmpty()) this.pendingPlan else listOfNotNull(this.pendingAction)
    return AiConversationalState(
        pendingAction = plan.firstOrNull(),
        pendingPlan = plan,
        candidateTaskIds = this.candidateIds,
        lastTaskId = this.lastTaskId,
        lastGoalId = this.lastGoalId,
        lastEntityTitle = this.lastEntityTitle
    )
}

