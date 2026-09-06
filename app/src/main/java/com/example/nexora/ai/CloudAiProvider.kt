package com.example.nexora.ai

/**
 * Placeholder for Cloud-based LLM provider.
 * 
 * IMPORTANT:
 * - Never put real API keys in this file.
 * - Production keys should be handled by a secure backend proxy.
 * - Local development keys should be loaded from a secure, non-committed config.
 */
class CloudAiProvider(
    private val config: CloudAiConfig
) : AiModelProvider {

    override val providerName: String = "Nexora Cloud Intelligence"

    override suspend fun generateResponse(
        prompt: String,
        context: AiContext
    ): AiModelResponse {
        // In a real implementation, this would make a network call to a secure proxy
        // which adds the API key and forwards to the LLM provider.
        
        // For now, it returns a message about cloud integration.
        return AiModelResponse(
            text = "Cloud intelligence is not yet connected. Using local intelligence instead.",
            modelName = "nexora-cloud-stub"
        )
    }

    override suspend fun generateStructuredResponse(
        prompt: String,
        context: AiContext
    ): AiModelStructuredResponse {
        // Real implementation would parse JSON output from LLM
        return AiModelStructuredResponse(
            decision = AiDecision(
                type = AiDecisionType.NO_ACTION,
                title = "Cloud Unavailable",
                reason = "Cloud provider is currently in stub mode."
            ),
            modelName = "nexora-cloud-stub"
        )
    }

    override suspend fun isAvailable(): Boolean {
        // Checks if API endpoint is reachable and config is valid
        return config.isConfigured
    }
}

data class CloudAiConfig(
    val endpoint: String? = null,
    val isConfigured: Boolean = false
)
