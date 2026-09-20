package com.example.nexora.ai

data class AiConversationalState(
    val pendingAction: AiAction? = null,
    val candidateTaskIds: List<Long> = emptyList(),
    val candidateGoalIds: List<Long> = emptyList(),
    val missingField: String? = null,
    val lastTaskId: Long? = null,
    val lastGoalId: Long? = null,
    val lastEntityTitle: String? = null
)

fun AiConversationalState.toConversationContext(): AiConversationContext {
    return AiConversationContext(
        pendingAction = this.pendingAction,
        candidateIds = this.candidateTaskIds,
        lastTaskId = this.lastTaskId,
        lastGoalId = this.lastGoalId,
        lastEntityTitle = this.lastEntityTitle
    )
}

fun AiConversationContext.toAiConversationalState(): AiConversationalState {
    return AiConversationalState(
        pendingAction = this.pendingAction,
        candidateTaskIds = this.candidateIds,
        lastTaskId = this.lastTaskId,
        lastGoalId = this.lastGoalId,
        lastEntityTitle = this.lastEntityTitle
    )
}

