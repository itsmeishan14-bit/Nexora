package com.example.nexora.ai

data class AiConversationalState(
    val pendingAction: AiAction? = null,
    val pendingPlan: List<AiAction> = emptyList(),
    val candidateTaskIds: List<Long> = emptyList(),
    val candidateGoalIds: List<Long> = emptyList(),
    val missingField: String? = null,
    val lastTaskId: Long? = null,
    val lastGoalId: Long? = null,
    val lastEntityTitle: String? = null,
    val activeClarification: AiClarification? = null,
    /** Bounded recent conversation history preserved across turns (max 20 messages). */
    val recentMessages: List<Pair<Boolean, String>> = emptyList()
)

fun AiConversationalState.toConversationContext(): AiConversationContext {
    val plan = if (this.pendingPlan.isNotEmpty()) this.pendingPlan else listOfNotNull(this.pendingAction)
    return AiConversationContext(
        pendingAction = plan.firstOrNull(),
        pendingPlan = plan,
        candidateIds = this.candidateTaskIds,
        lastTaskId = this.lastTaskId,
        lastGoalId = this.lastGoalId,
        lastEntityTitle = this.lastEntityTitle,
        activeClarification = this.activeClarification,
        recentMessages = this.recentMessages
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
        lastEntityTitle = this.lastEntityTitle,
        activeClarification = this.activeClarification,
        recentMessages = this.recentMessages
    )
}
