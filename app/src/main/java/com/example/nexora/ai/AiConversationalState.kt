package com.example.nexora.ai

data class AiConversationalState(
    val pendingAction: AiAction? = null,
    val candidateTaskIds: List<Long> = emptyList(),
    val candidateGoalIds: List<Long> = emptyList(),
    val missingField: String? = null
)
