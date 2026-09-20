package com.example.nexora.ai

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for AI model providers (Local, Cloud, etc.)
 */
interface AiModelProvider {
    
    val providerName: String
    
    /**
     * Generate a text response from the model.
     */
    suspend fun generateResponse(
        prompt: String,
        context: AiContext
    ): AiModelResponse

    suspend fun generateStructuredResponse(
        prompt: String,
        context: AiContext,
        conversationContext: AiConversationContext? = null
    ): AiModelStructuredResponse
    
    /**
     * Check if the provider is currently available.
     */
    suspend fun isAvailable(): Boolean
}

data class AiModelResponse(
    val text: String,
    val modelName: String,
    val usage: AiModelUsage? = null
)

data class AiModelStructuredResponse(
    val decision: AiDecision,
    val actions: List<AiAction> = emptyList(),
    val textResponse: String? = null,
    val modelName: String,
    val usage: AiModelUsage? = null,
    val candidateTaskIds: List<Long> = emptyList(),
    val candidateGoalIds: List<Long> = emptyList(),
    val conversationContext: AiConversationContext? = null
)

data class AiModelUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
