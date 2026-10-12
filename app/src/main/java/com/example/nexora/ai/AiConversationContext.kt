package com.example.nexora.ai

import java.time.LocalDate

/**
 * Temporal scope for natural language date understanding.
 */
enum class TemporalScope {
    TODAY,
    YESTERDAY,
    TOMORROW,
    THIS_WEEK,
    LAST_WEEK,
    NEXT_WEEK,
    THIS_MONTH,
    RECENTLY
}

/**
 * Concrete resolved date range for temporal queries.
 */
data class TemporalRange(
    val scope: TemporalScope,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val label: String
)

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
    val pendingAction: AiAction? = null,
    val pendingPlan: List<AiAction> = emptyList(),
    val candidateIds: List<Long> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * Bounded recent conversation history (max 10 turns = 20 messages).
     * Each entry is a pair: true = user message, false = assistant message.
     * Used to resolve follow-ups like "give me an example", "tell me more", "what about the second one".
     */
    val recentMessages: List<Pair<Boolean, String>> = emptyList()
) {
    /**
     * Context expires after 5 minutes of inactivity.
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - timestamp > 5 * 60 * 1000
    }

    /**
     * Returns the last assistant response in recent history, if any.
     */
    fun lastAssistantMessage(): String? = recentMessages.lastOrNull { !it.first }?.second

    /**
     * Returns the last user message in recent history, if any.
     */
    fun lastUserMessage(): String? = recentMessages.lastOrNull { it.first }?.second

    /**
     * Returns up to N recent turns as a formatted string for context injection.
     */
    fun recentHistoryAsText(maxTurns: Int = 5): String {
        val relevant = recentMessages.takeLast(maxTurns * 2)
        return relevant.joinToString("\n") { (isUser, text) ->
            if (isUser) "User: $text" else "Assistant: $text"
        }
    }

    /**
     * Appends a new message, keeping history bounded to the last 10 turns (20 messages).
     */
    fun appendMessage(isFromUser: Boolean, text: String): AiConversationContext {
        val maxMessages = 20
        val updated = (recentMessages + (isFromUser to text)).takeLast(maxMessages)
        return copy(recentMessages = updated, timestamp = System.currentTimeMillis())
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
 * Canonical single request interpretation produced by the language understanding pipeline.
 */
data class AiLanguageResult(
    val intent: AiDecisionType,
    val confidence: AiConfidence,
    val entities: Map<String, Any> = emptyMap(),
    val textResponse: String? = null,
    val clarificationNeeded: AiClarification? = null,
    val isConfirmation: Boolean = false,
    val isCancellation: Boolean = false,
    val temporalRange: TemporalRange? = null,
    val requiresMultiStepReasoning: Boolean = false,
    val requiresMutation: Boolean = false,
    val requiresClarification: Boolean = false,
    val targetTaskId: Long? = null,
    val targetGoalId: Long? = null,
    val targetTaskTitle: String? = null,
    val targetGoalTitle: String? = null,
    val requestedAction: AiActionType? = null
)

