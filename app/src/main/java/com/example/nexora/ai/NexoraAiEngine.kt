package com.example.nexora.ai

/**
 * Main entry point for Nexora AI capabilities.
 * Now powered by the Nexora AI Brain orchestration layer.
 */
class NexoraAiEngine(
    private val contextBuilder: AiContextBuilder,
    private val aiService: NexoraAiService,
    private val actionExecutor: AiActionExecutor,
    toolRegistry: AiToolRegistry,
    repository: com.example.nexora.data.NexoraRepository
) {
    private val brain = NexoraAiBrain(
        contextBuilder = contextBuilder,
        aiService = aiService,
        actionExecutor = actionExecutor,
        toolRegistry = toolRegistry,
        repository = repository
    )

    /**
     * Unified entry point for all AI requests.
     */
    suspend fun processRequest(request: AiRequest): AiResponse {
        return brain.processRequest(request)
    }

    // Legacy method wrappers to maintain backward compatibility during migration

    suspend fun executeAction(action: AiAction): AiActionResult {
        return actionExecutor.execute(action)
    }

    fun getAutomationRules(): List<AiAutomationRule> {
        return brain.getAutomationRules()
    }

    fun updateAutomationRule(rule: AiAutomationRule) {
        brain.updateAutomationRule(rule)
    }

    suspend fun analyze(): List<AiRecommendation> {
        val response = brain.processRequest(AiRequest(AiRequestType.GENERAL_ANALYSIS))
        return response.recommendations
    }

    suspend fun getProactiveInsights(): List<AiRecommendation> {
        val response = brain.processRequest(AiRequest(AiRequestType.PROACTIVE_ANALYSIS))
        return response.recommendations
    }

    suspend fun getContext(): AiContext {
        return contextBuilder.build()
    }

    suspend fun getTopInsight(): String? {
        val context = contextBuilder.build()
        return context.memory.items.firstOrNull()?.content ?: context.memory.legacyPatterns.firstOrNull()?.description
    }

    suspend fun createDailyPlan(): NexoraDailyPlan {
        // Daily plan currently returns a specific model used by the UI
        val context = contextBuilder.build()
        return aiService.generateDailyPlan(context)
    }

    suspend fun analyzeGoals(): List<AiRecommendation> {
        val context = contextBuilder.build()
        return aiService.analyzeGoals(context)
    }

    suspend fun analyzeProductivity(): List<AiRecommendation> {
        val context = contextBuilder.build()
        return aiService.analyzeProductivity(context)
    }

    suspend fun decomposeGoal(
        goalTitle: String,
        goalDescription: String = "",
        category: String = "Personal"
    ): AiGoalDecomposition {
        return aiService.decomposeGoal(
            goalTitle = goalTitle,
            goalDescription = goalDescription,
            category = category
        )
    }

    suspend fun ask(
        userMessage: String
    ): AiModelStructuredResponse {
        val context = contextBuilder.build()
        return aiService.askNexora(
            context = context,
            userMessage = userMessage
        )
    }
}
