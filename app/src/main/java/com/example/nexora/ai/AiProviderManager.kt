package com.example.nexora.ai

/**
 * Manages multiple AI providers and handles fallback logic.
 */
class AiProviderManager(
    private val localProvider: LocalAiProvider,
    private val cloudProvider: CloudAiProvider? = null
) {
    private var useCloudIfAvailable: Boolean = false

    suspend fun getActiveProvider(): AiModelProvider {
        if (useCloudIfAvailable && cloudProvider != null && cloudProvider.isAvailable()) {
            return cloudProvider
        }
        return localProvider
    }

    fun setUseCloud(enabled: Boolean) {
        useCloudIfAvailable = enabled
    }

    suspend fun generateResponse(
        prompt: String,
        context: AiContext
    ): AiModelResponse {
        return try {
            getActiveProvider().generateResponse(prompt, context)
        } catch (e: Exception) {
            // Fallback to local if cloud fails
            localProvider.generateResponse(prompt, context)
        }
    }

    suspend fun generateStructuredResponse(
        prompt: String,
        context: AiContext
    ): AiModelStructuredResponse {
        return try {
            getActiveProvider().generateStructuredResponse(prompt, context)
        } catch (e: Exception) {
            // Fallback to local if cloud fails
            localProvider.generateStructuredResponse(prompt, context)
        }
    }
}
