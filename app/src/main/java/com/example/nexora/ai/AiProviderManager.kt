package com.example.nexora.ai

import com.example.nexora.util.NexoraLogger

/**
 * Manages multiple AI providers and handles routing, fallback, and validation.
 */
class AiProviderManager(
    private val localProvider: LocalAiProvider,
    private val cloudProvider: AiModelProvider? = null
) {
    private var useCloudIfAvailable: Boolean = true

    /**
     * Determines the most appropriate provider for the current request.
     */
    suspend fun getActiveProvider(): AiModelProvider {
        if (useCloudIfAvailable && cloudProvider != null && cloudProvider.isAvailable()) {
            return cloudProvider
        }
        return localProvider
    }

    fun setUseCloud(enabled: Boolean) {
        useCloudIfAvailable = enabled
    }

    /**
     * Generates a conversational response with automatic local fallback.
     */
    suspend fun generateResponse(
        prompt: String,
        context: AiContext
    ): AiModelResponse {
        val provider = getActiveProvider()
        return try {
            NexoraLogger.d("AI", "Calling provider: ${provider.providerName}")
            provider.generateResponse(prompt, context)
        } catch (e: Exception) {
            NexoraLogger.w("AI", "Provider ${provider.providerName} failed: ${e.message}")
            localProvider.generateResponse(prompt, context)
        }
    }

    /**
     * Generates a structured decision/action with automatic local fallback and basic validation.
     */
    suspend fun generateStructuredResponse(
        prompt: String,
        context: AiContext
    ): AiModelStructuredResponse {
        val provider = getActiveProvider()
        return try {
            val response = provider.generateStructuredResponse(prompt, context)
            validateResponse(response, context)
        } catch (e: Exception) {
            NexoraLogger.w("AI", "Structured provider ${provider.providerName} failed: ${e.message}")
            localProvider.generateStructuredResponse(prompt, context)
        }
    }

    /**
     * Ensures the AI doesn't hallucinate non-existent IDs.
     */
    private fun validateResponse(
        response: AiModelStructuredResponse,
        context: AiContext
    ): AiModelStructuredResponse {
        // Factual Grounding: Verify Task/Goal IDs exist in context
        val validatedTaskId = response.decision.taskId?.takeIf { id ->
            context.tasks.any { it.id == id }
        }
        val validatedGoalId = response.decision.goalId?.takeIf { id ->
            context.goals.any { it.id == id }
        }

        if (validatedTaskId != response.decision.taskId || validatedGoalId != response.decision.goalId) {
            NexoraLogger.w("AI", "Provider returned non-existent Task/Goal ID. Nullifying to prevent errors.")
        }

        return response.copy(
            decision = response.decision.copy(
                taskId = validatedTaskId,
                goalId = validatedGoalId
            )
        )
    }
}
