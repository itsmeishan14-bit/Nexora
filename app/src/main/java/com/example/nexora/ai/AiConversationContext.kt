package com.example.nexora.ai

import java.util.UUID

/**
 * Lightweight conversational context to support references like "it", "the first one",
 * and multi-step clarification flows.
 */
data class AiConversationContext(
    val lastIntent: AiDecisionType? = null,
    val lastTaskId: Long? = null,
    val lastGoalId: Long? = null,
    val lastEntityTitle: String? = null,
    val activeClarification: AiClarification? = null,
    val candidateIds: List<Long> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Context expires after 5 minutes of inactivity.
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - timestamp > 5 * 60 * 1000
    }
}

/**
 * Represents a state where the AI needs more information from the user.
 */
data class AiClarification(
    val question: String,
    val intent: AiDecisionType,
    val missingField: String,
    val partialEntities: Map<String, Any> = emptyMap(),
    val candidates: List<Long> = emptyList(),
    val originalQuery: String
)

/**
 * Result of the advanced local language pipeline.
 */
data class AiLanguageResult(
    val intent: AiDecisionType,
    val confidence: AiConfidence,
    val entities: Map<String, Any> = emptyMap(),
    val textResponse: String? = null,
    val clarificationNeeded: AiClarification? = null
)
