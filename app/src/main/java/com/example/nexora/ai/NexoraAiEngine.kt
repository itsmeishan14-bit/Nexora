package com.example.nexora.ai

class NexoraAiEngine(
    private val contextBuilder: AiContextBuilder,
    private val aiService: NexoraAiService,
    private val actionExecutor: AiActionExecutor
) {

    suspend fun executeAction(action: AiAction): AiActionResult {
        return actionExecutor.execute(action)
    }

    suspend fun analyze(): List<AiRecommendation> {
        val context = contextBuilder.build()
        return aiService.generateRecommendations(context)
    }

    suspend fun getProactiveInsights(): List<AiRecommendation> {
        val context = contextBuilder.build()
        return aiService.generateProactiveInsights(context)
    }

    suspend fun getContext(): AiContext {
        return contextBuilder.build()
    }

    suspend fun getTopInsight(): String? {
        val context = contextBuilder.build()
        return context.memory.patterns.firstOrNull()?.description
    }

    suspend fun createDailyPlan(): NexoraDailyPlan {
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
        goalDescription: String = ""
    ): AiGoalDecomposition {
        return aiService.decomposeGoal(
            goalTitle = goalTitle,
            goalDescription = goalDescription
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
