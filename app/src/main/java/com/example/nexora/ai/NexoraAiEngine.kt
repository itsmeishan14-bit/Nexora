package com.example.nexora.ai

class NexoraAiEngine(
    private val contextBuilder: AiContextBuilder,
    private val aiService: NexoraAiService
) {

    suspend fun analyze(): List<AiRecommendation> {
        val context = contextBuilder.build()
        return aiService.generateRecommendations(context)
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

        val context = contextBuilder.build()

        return aiService.decomposeGoal(
            context = context,
            goalTitle = goalTitle,
            goalDescription = goalDescription
        )
    }

    suspend fun ask(
        userMessage: String
    ): String {

        val context = contextBuilder.build()

        return aiService.askNexora(
            context = context,
            userMessage = userMessage
        )
    }

    suspend fun decide(
        query: String
    ): AiModelStructuredResponse {
        val context = contextBuilder.build()
        
        return if (aiService is LocalNexoraAiService) {
            aiService.decide(context, query)
        } else {
            // Fallback for other implementations
            AiModelStructuredResponse(
                decision = AiDecision(
                    type = AiDecisionType.NO_ACTION,
                    title = "Unsupported",
                    reason = "This AI service does not support structured decisions yet."
                ),
                modelName = "unknown"
            )
        }
    }
}
